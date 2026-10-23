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

/** Downloads the public dataset as a whole. No food query, photo or profile is uploaded. */
class TfdaNutritionRepository(context: Context) : NutritionLookupSource {
    private val cache = AtomicFile(File(context.cacheDir, "tfda-nutrition-v1.json"))
    private val mutex = Mutex()
    private var memory: Pair<Long, List<NutritionFood>>? = null

    override suspend fun lookup(foodName: String): NutritionLookup = withContext(Dispatchers.IO) {
        mutex.withLock {
            val old = memory ?: readCache()?.also { memory = it }
            val now = System.currentTimeMillis()
            if (old != null && now - old.first in 0 until 7 * 24 * 60 * 60 * 1000L) {
                return@withLock match(foodName, old, true)
            }
            try {
                val foods = download()
                require(foods.size in 1000..10000) { "營養資料不完整" }
                val fresh = System.currentTimeMillis() to foods
                memory = fresh
                // A full cache or a temporary disk failure must not discard a valid download.
                runCatching { writeCache(fresh) }
                match(foodName, fresh, false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("FitAI_Nutrition", "Public nutrition dataset unavailable", error)
                if (old != null) match(foodName, old, true, "更新失敗，使用已下載的資料。")
                else NutritionLookup(message = "營養資料暫時無法取得，已改用本地估算。")
            }
        }
    }

    private fun match(query: String, data: Pair<Long, List<NutritionFood>>, cached: Boolean, prefix: String = ""): NutritionLookup {
        val foods = NutritionMatcher.find(query, data.second)
        return if (foods.isEmpty()) NutritionLookup(message = "${prefix}沒有符合名稱的營養資料，使用本地估算。")
        else NutritionLookup(NutritionReference(data.first, cached, foods), "${prefix}已提供食藥署營養資料，繼續由本地 AI 估算。")
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
