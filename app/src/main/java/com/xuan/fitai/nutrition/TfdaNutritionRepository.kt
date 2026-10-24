package com.xuan.fitai.nutrition

import android.content.Context
import android.util.AtomicFile
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Taiwan public meal tables plus TFDA's dataset, supplemented by marked approximate recipes. */
class TfdaNutritionRepository(
    context: Context,
    private val webSearch: suspend (String) -> List<NutritionFood> = WebNutritionScraper::searchOnce,
) : NutritionLookupSource {
    private val cache = AtomicFile(File(context.cacheDir, "tfda-nutrition-v1.json"))
    private val mutex = Mutex()
    private var memory: Pair<Long, List<NutritionFood>>? = null
    private val webCache = object : LinkedHashMap<String, Pair<Long, List<NutritionFood>>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<NutritionFood>>>?): Boolean = size > 100
    }

    override suspend fun lookup(foodName: String): NutritionLookup = withContext(Dispatchers.IO) {
        mutex.withLock {
            // One bounded Taiwan public meal page; food ingredients fall back to TFDA.
            val cacheKey = foodName.trim().lowercase()
            val cachedWeb = webCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.first in 0 until 24 * 60 * 60 * 1000L }
            var webFetchedAt = cachedWeb?.first ?: System.currentTimeMillis()
            val webFoods = if (cachedWeb != null) {
                cachedWeb.second
            } else {
                val scraped = try { withTimeoutOrNull(3000) { webSearch(foodName) }.orEmpty() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                if (scraped.isNotEmpty()) {
                    webFetchedAt = System.currentTimeMillis()
                    webCache[cacheKey] = webFetchedAt to scraped
                }
                scraped
            }

            // Step 2 (Supplement / Fallback): Local Taiwanese Common Foods + TFDA dataset
            val old = memory ?: readCache()?.also { memory = it }
            val now = System.currentTimeMillis()
            var tfdaFromCache = true
            val tfdaData: Pair<Long, List<NutritionFood>> = if (old != null && now - old.first in 0 until 7 * 24 * 60 * 60 * 1000L) {
                old
            } else if (webFoods.isEmpty() && NutritionMatcher.find(foodName, TaiwaneseCommonFoods.ALL).isEmpty()) {
                // Only block on downloading the 5 MB TFDA dataset if web scraper didn't already find enough
                try {
                    val foods = withTimeoutOrNull(16_000) { download() } ?: return@withLock combineResults(
                        foodName, webFoods, cachedWeb != null, webFetchedAt, old ?: (now to emptyList()), true)
                    require(foods.size in 1000..10000) { "營養資料不完整" }
                    (System.currentTimeMillis() to foods).also { fresh ->
                        memory = fresh
                        tfdaFromCache = false
                        runCatching { writeCache(fresh) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    android.util.Log.w("FitAI_Nutrition", "Public nutrition dataset unavailable", error)
                    old ?: (now to emptyList())
                }
            } else {
                old ?: (now to emptyList())
            }

            combineResults(
                query = foodName,
                webFoods = webFoods,
                webFromCache = cachedWeb != null,
                webFetchedAt = webFetchedAt,
                tfdaData = tfdaData,
                tfdaFromCache = tfdaFromCache
            )
        }
    }

    private fun combineResults(
        query: String,
        webFoods: List<NutritionFood>,
        webFromCache: Boolean,
        webFetchedAt: Long,
        tfdaData: Pair<Long, List<NutritionFood>>,
        tfdaFromCache: Boolean,
    ): NutritionLookup {
        val taggedTfda = tfdaData.second.map { food ->
            if (food.description.startsWith("【來源：")) food
            else food.copy(description = "【來源：衛福部食藥署 TFDA】${food.description}".take(400))
        }
        val localMatches = NutritionMatcher.find(query, TaiwaneseCommonFoods.ALL + taggedTfda)

        // Put Step 1 (Web Scraper) results first, supplemented by exact/top Step 2 local matches up to 5 items
        val merged = buildList {
            // If local database has an exact name match (e.g. "起司蛋餅", "紅燒牛肉麵") not literally named in webFoods,
            // include it right alongside the web scraper results so the AI gets both!
            addAll(webFoods.take(3))
            for (local in localMatches) {
                if (size >= 5) break
                if (none { it.name == local.name }) {
                    add(local)
                }
            }
            if (size < 5) {
                for (extraWeb in webFoods.drop(3)) {
                    if (size >= 5) break
                    if (none { it.name == extraWeb.name }) add(extraWeb)
                }
            }
        }

        if (merged.isEmpty()) {
            return NutritionLookup(message = "沒有可靠的相符營養資料，使用本地 AI 估算。")
        }

        val webCount = merged.count { it.id.startsWith("WEB-") }
        val twCount = merged.count { it.id.startsWith("TW-") }
        val tfdaCount = merged.size - webCount - twCount

        val parts = buildList {
            if (webCount > 0) add("網路營養資料 ${webCount}筆")
            if (twCount > 0) add("內建配方估算 ${twCount}筆")
            if (tfdaCount > 0) add("食藥署(TFDA) ${tfdaCount}筆")
        }
        val summaryText = if (webCount > 0) {
            "資料來源：${parts.joinToString(" ＋ ")}。"
        } else {
            "網路未找到可用資料，改由 ${parts.joinToString(" ＋ ")} 提供參考。"
        }

        return NutritionLookup(
            reference = NutritionReference(
                buildList { if (webCount > 0) add(webFetchedAt); if (tfdaCount > 0) add(tfdaData.first) }.minOrNull() ?: System.currentTimeMillis(),
                (webCount == 0 || webFromCache) && (tfdaCount == 0 || tfdaFromCache), merged),
            message = summaryText
        )
    }

    private suspend fun download(): List<NutritionFood> {
        val connection = URL(TFDA_DOWNLOAD_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "FitAI/1.3 (Android; nutrition reference)")
            require(connection.responseCode == 200)
            val compressed = readLimited(connection.inputStream, 8 * 1024 * 1024)
            return ZipInputStream(compressed.inputStream()).use { zip ->
                val entry = zip.nextEntry ?: error("空的營養資料")
                require(entry.name.endsWith(".json"))
                TfdaDatasetParser.parse(BoundedInputStream(zip, 160 * 1024 * 1024))
            }
        } finally { connection.disconnect() }
    }

    private suspend fun readLimited(input: InputStream, limit: Int): ByteArray = input.use {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(32768)
        while (true) {
            currentCoroutineContext().ensureActive()
            val size = it.read(buffer); if (size < 0) break
            require(out.size() + size <= limit) { "資料下載過大" }
            out.write(buffer, 0, size)
        }
        out.toByteArray()
    }

    private fun readCache(): Pair<Long, List<NutritionFood>>? = runCatching {
        require(cache.baseFile.length() <= 4 * 1024 * 1024)
        val root = JSONObject(cache.openRead().bufferedReader().use { it.readText() })
        val time = root.getLong("fetchedAt"); require(time > 0 && time <= System.currentTimeMillis())
        val rows = root.getJSONArray("foods"); require(rows.length() in 1000..10000)
        time to (0 until rows.length()).map { NutritionReferenceJson.foodFromJson(rows.getJSONObject(it)) }
    }.getOrNull()

    private fun writeCache(data: Pair<Long, List<NutritionFood>>) {
        val bytes = JSONObject().put("fetchedAt", data.first)
            .put("foods", JSONArray(data.second.map(NutritionReferenceJson::foodToJson))).toString().toByteArray()
        val stream = cache.startWrite()
        try { stream.write(bytes); cache.finishWrite(stream) }
        catch (error: Exception) { cache.failWrite(stream); throw error }
    }
}

private class BoundedInputStream(input: InputStream, private val max: Long) : FilterInputStream(input) {
    private var count = 0L
    override fun read(): Int = super.read().also { if (it >= 0) { count++; require(count <= max) } }
    override fun read(b: ByteArray, off: Int, len: Int): Int = `in`.read(b, off, len).also {
        if (it > 0) { count += it; require(count <= max) }
    }
}

/** The published JSON uses an array of single-field objects for each nutrient row. */
object TfdaDatasetParser {
    suspend fun parse(input: InputStream): List<NutritionFood> {
        val foods = linkedMapOf<String, MutableMap<String, String>>()
        val wanted = setOf("整合編號", "樣品名稱", "俗名", "內容物描述", "分析項", "含量單位", "每100克含量", "食品分類")
        val nutrients = setOf("熱量", "粗蛋白", "粗脂肪", "總碳水化合物")
        JsonReader(InputStreamReader(input, Charsets.UTF_8).buffered()).use { reader ->
            reader.beginArray()
            var rows = 0
            while (reader.hasNext()) {
                if (rows++ % 256 == 0) currentCoroutineContext().ensureActive()
                require(rows <= 300000)
                val row = mutableMapOf<String, String>()
                fun readObject() {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        if (key in wanted && reader.peek() == JsonToken.STRING) row[key] = reader.nextString().trim()
                        else reader.skipValue()
                    }
                    reader.endObject()
                }
                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray(); while (reader.hasNext()) readObject(); reader.endArray()
                } else readObject()
                val nutrient = row["分析項"] ?: continue
                if (nutrient !in nutrients || row["含量單位"] != if (nutrient == "熱量") "kcal" else "g") continue
                val id = row["整合編號"] ?: continue
                require(foods.size <= 10000)
                val food = foods.getOrPut(id) { row.toMutableMap() }
                row["每100克含量"]?.let { food[nutrient] = it }
            }
            reader.endArray()
        }
        return foods.mapNotNull { (id, row) ->
            val cal = row["熱量"]?.toFloatOrNull() ?: return@mapNotNull null
            val protein = row["粗蛋白"]?.toFloatOrNull() ?: return@mapNotNull null
            val fat = row["粗脂肪"]?.toFloatOrNull() ?: return@mapNotNull null
            val carbs = row["總碳水化合物"]?.toFloatOrNull() ?: return@mapNotNull null
            NutritionFood(id, row["樣品名稱"].orEmpty().take(100), row["俗名"].orEmpty().take(500),
                row["內容物描述"].orEmpty().take(400), cal, protein, carbs, fat, row["食品分類"].orEmpty().take(40)).takeIf { it.isValid() }
        }
    }
}
