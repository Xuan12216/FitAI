package com.xuan.fitai.nutrition

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

/** Bounded requests to Taiwan public meal tables and FatSecret public nutrition lookup. */
object WebNutritionScraper {
    const val FATSECRET_SEARCH_URL = "https://www.fatsecret.cn/%E7%83%AD%E9%87%8F%E8%90%A5%E5%85%BB/search?q="
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val rows = Regex("<tr\\b[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val cells = Regex("<td\\b[^>]*>(.*?)</td>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val fatSecretCells = Regex("<td\\s+class=\"borderBottom\"[^>]*>(.*?)</td>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val fatSecretTitle = Regex("<a\\s+class=\"prominent\"[^>]*>(.*?)</a>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val fatSecretBrand = Regex("<a\\s+class=\"brand\"[^>]*>\\((.*?)\\)</a>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val fatSecretSummary = Regex("<div\\s+class=\"smallText\\s+greyText\\s+greyLink\"[^>]*>(.*?)</div>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val fatSecretMacros = Regex(
        "每\\s*([^|-]+?)\\s*-\\s*卡路里:\\s*(\\d+(?:\\.\\d+)?)\\s*千卡\\s*\\|\\s*脂肪:\\s*(\\d+(?:\\.\\d+)?)\\s*克\\s*\\|\\s*碳水物:\\s*(\\d+(?:\\.\\d+)?)\\s*克\\s*\\|\\s*蛋白质:\\s*(\\d+(?:\\.\\d+)?)\\s*克"
    )
    private val gramsInServing = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:克|g\\b)", RegexOption.IGNORE_CASE)
    private val tags = Regex("<[^>]+>")
    private val whitespace = Regex("\\s+")

    internal fun normalizeQuery(raw: String): String = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC)
        .replace("臺", "台").replace("捲", "卷").replace("起士", "起司").replace("芝士", "起司")
        .replace("魯肉飯", "滷肉飯").replace("咔啦", "卡拉").replace(whitespace, "")

    /** Select one known page, never crawl search results or send personal data. */
    internal fun sourcePageUrl(query: String): String? {
        val key = normalizeQuery(query)
        val page = when {
            listOf("蛋餅", "吐司", "三明治", "蛋堡", "貝果", "麵包", "飯糰", "蘿蔔糕").any(key::contains) -> "breakfast"
            listOf("抓餅", "乳酪餅", "鬆餅", "披薩", "煎餃", "水餃", "薯條", "雞塊", "雞卷").any(key::contains) -> "kcal2"
            listOf("麵", "粄條", "米粉", "冬粉").any(key::contains) -> "kcal"
            key.contains("飯") || key.contains("丼") -> "rice"
            key.endsWith("湯") || key.endsWith("羹") -> "soup"
            else -> return null
        }
        return "https://meals.ntu.edu.tw/CampusDining/kcal/$page"
    }

    suspend fun searchOnce(foodName: String): List<NutritionFood> {
        if (foodName.trim().length !in 2..60) return emptyList()
        val ntuResults = sourcePageUrl(foodName)?.let { source ->
            fetchPage(source)?.let { parseTaiwanMealHtml(foodName, it, source) }
        }.orEmpty()
        val fatSecretResults = searchFatSecretOnce(foodName)
        return (fatSecretResults + ntuResults).distinctBy { it.name }.take(5)
    }

    private suspend fun searchFatSecretOnce(rawQuery: String): List<NutritionFood> {
        val q = buildFatSecretQuery(rawQuery)
        if (q.length < 2) return emptyList()
        val url = FATSECRET_SEARCH_URL + URLEncoder.encode(q, "UTF-8")
        val html = fetchPage(url, browserUa = true) ?: return emptyList()
        return parseFatSecretHtml(rawQuery, html)
    }

    internal fun buildFatSecretQuery(rawQuery: String): String {
        val q = Normalizer.normalize(rawQuery.trim(), Normalizer.Form.NFKC).replace("捲", "卷")
        if (q.endsWith("牛肉麵") && q.length > 3) return "牛肉麵"
        if (q.endsWith("蛋餅") && q != "玉米蛋餅" && q.length > 2) return "蛋餅"
        for (brand in listOf("麥當勞", "肯德基", "星巴克", "摩斯漢堡", "摩斯", "漢堡王")) {
            if (q.startsWith(brand) && q.length > brand.length + 1) return q.removePrefix(brand).trim()
        }
        return q
    }

    internal fun parseFatSecretHtml(rawQuery: String, html: String): List<NutritionFood> {
        if (html.isBlank()) return emptyList()
        val key = normalizeQuery(rawQuery).lowercase(Locale.ROOT)
        val sq = normalizeQuery(buildFatSecretQuery(rawQuery)).lowercase(Locale.ROOT)
        return fatSecretCells.findAll(html).mapNotNull { cellMatch ->
            val cell = cellMatch.groupValues[1]
            val rawTitle = fatSecretTitle.find(cell)?.groupValues?.get(1)?.let(::stripHtml).orEmpty()
            if (rawTitle.isEmpty()) return@mapNotNull null
            val brand = fatSecretBrand.find(cell)?.groupValues?.get(1)?.let(::stripHtml).orEmpty()
            val summary = fatSecretSummary.find(cell)?.groupValues?.get(1)?.let(::stripHtml).orEmpty()
            val macroMatch = fatSecretMacros.find(summary) ?: return@mapNotNull null

            val servingText = macroMatch.groupValues[1].trim()
            val rawCal = macroMatch.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
            val rawFat = macroMatch.groupValues[3].toFloatOrNull() ?: return@mapNotNull null
            val rawCarbs = macroMatch.groupValues[4].toFloatOrNull() ?: return@mapNotNull null
            val rawProtein = macroMatch.groupValues[5].toFloatOrNull() ?: return@mapNotNull null

            val displayName = if (brand.isNotEmpty() && !rawTitle.contains(brand)) "$brand $rawTitle" else rawTitle
            val normTitle = normalizeQuery(displayName).lowercase(Locale.ROOT)
            val overlap = sq.count { normTitle.contains(it) }
            if (overlap == 0 && !normTitle.contains(key)) return@mapNotNull null

            val grams = gramsInServing.find(servingText)?.groupValues?.get(1)?.toFloatOrNull()
            val scale = if (grams != null && grams >= 10f && grams <= 2000f) 100f / grams else 1f
            val cal100 = (rawCal * scale * 10f).roundToInt() / 10f
            val p100 = (rawProtein * scale * 10f).roundToInt() / 10f
            val c100 = (rawCarbs * scale * 10f).roundToInt() / 10f
            val f100 = (rawFat * scale * 10f).roundToInt() / 10f

            val desc = if (grams != null && grams != 100f) {
                "【來源：網路營養資料庫 FatSecret】每$servingText 約 ${rawCal.roundToInt()}kcal (P ${rawProtein}g/C ${rawCarbs}g/F ${rawFat}g)；已換算每100g"
            } else {
                "【來源：網路營養資料庫 FatSecret】每$servingText：熱量 ${rawCal.roundToInt()}kcal (P ${rawProtein}g/C ${rawCarbs}g/F ${rawFat}g)"
            }
            NutritionFood(
                id = "WEB-FS-${displayName.hashCode().toUInt().toString(16)}",
                name = displayName.take(100),
                aliases = rawQuery.take(100),
                description = desc.take(400),
                calories = cal100,
                protein = p100,
                carbs = c100,
                fat = f100,
                category = "網路營養資料 (FatSecret)",
                sourceUrl = "https://www.fatsecret.cn"
            ).takeIf { it.isValid() }
        }.distinctBy { it.name }.take(3).toList()
    }

    private suspend fun fetchPage(url: String, browserUa: Boolean = false): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            connection.instanceFollowRedirects = browserUa
            connection.setRequestProperty("User-Agent", if (browserUa) BROWSER_USER_AGENT else "FitAI/1.3.2 (Android; nutrition reference)")
            connection.setRequestProperty("Accept", "text/html")
            connection.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9")
            if (connection.responseCode != 200) return null
            val bytes = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16384)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val size = input.read(buffer)
                    if (size < 0) break
                    if (out.size() + size > 512 * 1024) return null
                    out.write(buffer, 0, size)
                }
                out.toByteArray()
            }
            return bytes.toString(Charsets.UTF_8)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseTaiwanMealHtml(query: String, html: String, sourceUrl: String): List<NutritionFood> {
        if (sourceUrl !in NTU_MEAL_SOURCE_URLS) return emptyList()
        val key = normalizeQuery(query)
        return rows.findAll(html).mapNotNull { row ->
            val values = cells.findAll(row.groupValues[1]).map { stripHtml(it.groupValues[1]) }.toList()
            if (values.size < 2) return@mapNotNull null
            // Only remove the published English translation, never a Chinese ingredient modifier.
            val name = values[0].replace(Regex("\\s*[（(]?[A-Za-z].*$"), "").trim()
            if (normalizeQuery(name) != key) return@mapNotNull null
            // Reject ambiguous thin/thick values and rows with an unparsed serving count.
            val calories = values[1].toFloatOrNull() ?: return@mapNotNull null
            NutritionFood(
                id = "WEB-NTU-$name", name = name, aliases = "",
                description = "【來源：臺大膳食協調委員會餐點熱量表】每份參考熱量；未公布克重與三大營養素。配方與份量以現場供應為主。",
                calories = calories, protein = 0f, carbs = 0f, fat = 0f,
                category = "網路營養資料 (臺大餐點表)", sourceUrl = sourceUrl,
                servingLabel = "1份", hasMacros = false
            ).takeIf { it.isValid() }
        }.distinctBy { it.name }.take(5).toList()
    }

    private fun stripHtml(raw: String): String = raw.replace(tags, " ")
        .replace("&nbsp;", " ").replace("&#160;", " ").replace("&amp;", "&")
        .replace(whitespace, " ").trim()
}
