package com.xuan.fitai.nutrition

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

/** One bounded request to a Taiwan public meal table. Ingredients fall back to TFDA. */
object WebNutritionScraper {
    private val rows = Regex("<tr\\b[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val cells = Regex("<td\\b[^>]*>(.*?)</td>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val tags = Regex("<[^>]+>")
    private val whitespace = Regex("\\s+")

    internal fun normalizeQuery(raw: String): String = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC)
        .replace("臺", "台").replace("捲", "卷").replace("起士", "起司").replace("芝士", "起司")
        .replace("魯肉飯", "滷肉飯").replace(whitespace, "")

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
        val source = sourcePageUrl(foodName) ?: return emptyList()
        return fetchPage(source)?.let { parseTaiwanMealHtml(foodName, it, source) }.orEmpty()
    }

    private suspend fun fetchPage(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 1500
            connection.readTimeout = 1500
            // Source links and redirects must stay on the selected Taiwan source.
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "FitAI/1.3.2 (Android; nutrition reference)")
            connection.setRequestProperty("Accept", "text/html")
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
