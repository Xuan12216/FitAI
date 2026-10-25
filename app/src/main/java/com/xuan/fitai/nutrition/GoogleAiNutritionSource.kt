package com.xuan.fitai.nutrition

import android.content.Context
import com.xuan.fitai.ai.GemmaFoodAnalysis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.text.Normalizer

fun interface GoogleAiLookupSource {
    suspend fun lookup(name: String, portion: String): GoogleAiLookup
    suspend fun retry(name: String, portion: String): GoogleAiLookup = lookup(name, portion)
    suspend fun lookupWithProgress(name: String, portion: String, retry: Boolean, onStatus: suspend (String) -> Unit): GoogleAiLookup =
        if (retry) this.retry(name, portion) else lookup(name, portion)
}
data class GoogleAiLookup(val assessment: GoogleAiAssessment? = null, val message: String, val attempts: Int = 0, val canRetry: Boolean = false)
data class GoogleAiAssessment(val analysis: GemmaFoodAnalysis, val reference: NutritionReference) {
    fun json(): String = buildJsonObject {
        put("calories", analysis.calories); put("protein", analysis.protein)
        put("carbs", analysis.carbs); put("fat", analysis.fat); put("suitable", analysis.isSuitable)
        put("advice", analysis.advice); put("reasoning", analysis.reasoning)
        put("referenceFoodId", analysis.referenceFoodId)
    }.toString()
}

/** At most three distinct providers per round. Captcha/rate limits stop immediately. */
class GoogleAiNutritionSource(
    private val pages: GoogleAiPageSource,
    private val clock: () -> Long = System::currentTimeMillis,
    val monitor: GoogleAiTrafficMonitor = GoogleAiTrafficMonitor(),
    private val report: (GoogleAiTrafficMonitor.Snapshot) -> Unit = {},
    private val rotation: WebAiRotation? = null,
) : GoogleAiLookupSource {
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, GoogleAiAssessment>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GoogleAiAssessment>?) = size > 32
    }

    override suspend fun lookup(name: String, portion: String): GoogleAiLookup = lookupLimited(name, portion, false)
    override suspend fun retry(name: String, portion: String): GoogleAiLookup = lookupLimited(name, portion, true)
    override suspend fun lookupWithProgress(name: String, portion: String, retry: Boolean, onStatus: suspend (String) -> Unit): GoogleAiLookup =
        lookupLimited(name, portion, retry, onStatus)

    private suspend fun lookupLimited(name: String, portion: String, manualRetry: Boolean, onStatus: suspend (String) -> Unit = {}): GoogleAiLookup {
        if (!mutex.tryLock()) {
            monitor.suppressed()
            report(monitor.snapshot())
            return GoogleAiLookup(message = "已有網路 AI 查詢進行中，已略過重複請求。")
        }
        try { return lookupLocked(name, portion, manualRetry, onStatus) } finally { mutex.unlock() }
    }

    private suspend fun lookupLocked(name: String, portion: String, manualRetry: Boolean, onStatus: suspend (String) -> Unit): GoogleAiLookup {
        // A pinned source (used by diagnostics) cannot switch providers on a manual retry.
        val limit = if (manualRetry && rotation == null) 1 else 3
        val fallback = if (manualRetry) "本次未更新估算，原有數值已保留。" else "改用台灣資料。"
        val basis = GoogleAiNutritionParser.basisForPortion(portion)
            ?: return GoogleAiLookup(message = "份量無法明確對應，$fallback")
        if (name.trim().length !in 2..80) return GoogleAiLookup(message = "餐點名稱無法查詢，$fallback")
        val key = WebNutritionScraper.normalizeQuery(name) + "|" + basis
        val now = clock()
        cache[key]?.takeIf { now - it.reference.fetchedAt in 0 until 24 * 60 * 60 * 1000L }?.let {
            monitor.cacheHit()
            report(monitor.snapshot())
            return GoogleAiLookup(it.copy(reference = it.reference.copy(fromCache = true)), "使用 ${it.reference.foods.first().webAiProvider?.label ?: "網路 AI"} 的近期估算快取。")
        }
        val tried = mutableSetOf<WebAiProvider>()
        for (attempt in 1..limit) {
            if (attempt > 1 && monitor.snapshot().dense) {
                return GoogleAiLookup(message = "近期查詢較密集，已停止追加自動重試；可手動再查一次或使用本地結果。", attempts = attempt - 1, canRetry = true)
            }
            val provider = if (rotation == null) WebAiProvider.GOOGLE else rotation.next(tried)
                ?: return GoogleAiLookup(message = "目前沒有其他可輪替的 AI。$fallback", attempts = attempt - 1)
            tried += provider
            val url = providerUrl(provider, name, basis, if (manualRetry) 2 else attempt)
            onStatus((if (attempt > 1) "前一個 AI 未取得可用資料，切換至 " else "正在查詢 ") +
                "${provider.label}（第 $attempt/$limit 家，回答最多等 30 秒）…")
            monitor.started(provider.label)
            val startedAt = System.nanoTime()
            fun record(outcome: GoogleAiTrafficMonitor.Outcome, status: Int? = null) {
                monitor.finished(outcome, (System.nanoTime() - startedAt) / 1_000_000, status)
                report(monitor.snapshot())
            }
            val page = try { pages.loadUntilReady(url) { page ->
                GoogleAiNutritionParser.parse(name, basis, page, url, provider)?.let { "${it.servingLabel}|${it.calories}|${it.protein}|${it.carbs}|${it.fat}" }
            } }
                catch (cancelled: CancellationException) { record(GoogleAiTrafficMonitor.Outcome.CANCELLED); throw cancelled }
                catch (_: Exception) {
                    record(GoogleAiTrafficMonitor.Outcome.NETWORK_ERROR)
                    if (rotation != null && attempt < limit) continue
                    return GoogleAiLookup(message = "${provider.label} 暫時無法取得。$fallback", attempts = attempt, canRetry = true)
                }
            if (page.blocked) {
                record(GoogleAiTrafficMonitor.Outcome.BLOCKED, page.httpStatus)
                return GoogleAiLookup(message = "${provider.label} 要求驗證或限制存取，已停止本次網路查詢。$fallback", attempts = attempt)
            }
            if (page.failure != null) {
                record(if (page.failure.contains("逾時")) GoogleAiTrafficMonitor.Outcome.TIMEOUT else GoogleAiTrafficMonitor.Outcome.NETWORK_ERROR, page.httpStatus)
                if (rotation != null && attempt < limit) continue
                return GoogleAiLookup(message = "${page.failure}$fallback", attempts = attempt, canRetry = true)
            }
            val food = GoogleAiNutritionParser.parse(name, basis, page, url, provider)
            if (food != null) {
                val recipe = GoogleAiNutritionParser.recipeDetails(page)
                val reference = NutritionReference(clock(), false, listOf(food), food.id,
                    page.text.take(1800), page.citations.filter { it.url.length <= 700 && isGoogleCitationUrl(it.url) }.take(2),
                    recipe.estimated, recipe.assumption)
                val result = GoogleAiAssessment(
                    GemmaFoodAnalysis(food.calories, food.protein, food.carbs, food.fat, true,
                        (if (recipe.estimated) "${food.category} 配方估算，非完整營養標示；假設：${recipe.assumption}。" else
                            "${food.category} 依公開網頁估算，請確認實際份量、配料與烹調方式；") + "尚未評估個人目標適合度。",
                        "${food.category} 的估算以每${food.servingLabel}為基準；數值若為區間，取中值顯示。非官方營養標示。", referenceFoodId = food.id), reference)
                record(GoogleAiTrafficMonitor.Outcome.SUCCESS, page.httpStatus)
                cache[key] = result
                return GoogleAiLookup(result, "已取得 ${food.category} 營養估算；本次網路查詢 $attempt 次。", attempt)
            }
            record(GoogleAiTrafficMonitor.Outcome.INCOMPLETE, page.httpStatus)
        }
        return GoogleAiLookup(message = "網路 AI 已回覆，但餐點名稱、份量或營養數值不完整；已達 $limit 次上限。$fallback", attempts = limit, canRetry = true)
    }

    companion object {
        internal fun providerUrl(provider: WebAiProvider, name: String, basis: String, attempt: Int): String {
            val base = when (provider) {
                WebAiProvider.GOOGLE -> return searchUrl(name, basis, attempt)
                WebAiProvider.GEMINI -> "https://gemini.google.com/app?q="
                WebAiProvider.CHATGPT -> "https://chatgpt.com/?q="
                WebAiProvider.BRAVE -> "https://search.brave.com/ask?source=llmSuggest&q="
                WebAiProvider.DUCK -> "https://duck.ai/?q="
            }
            return base + URLEncoder.encode(quickQuery(name, basis, attempt), "UTF-8")
        }
        internal fun searchUrl(name: String, basis: String, attempt: Int): String =
            "https://www.google.com/search?udm=50&hl=zh-TW&q=" + URLEncoder.encode(quickQuery(name, basis, attempt), "UTF-8")

        private fun quickQuery(name: String, basis: String, attempt: Int): String {
            // Compact control text leaves room for the full 80-character CJK meal name.
            val format = if (attempt == 2) "One Markdown table row (no CSV)" else "One Markdown table row"
            return "繁體中文. Quick lookup「${name.trim()}」每$basis. TW first,max2 sources. No deep thinking/comparison. " +
                "If no complete label,estimate recipe. $format: 餐點,基準,熱量kcal,蛋白質g,碳水g,脂肪g. " +
                "Then 類型:資料 or 配方估算; 假設:ingredients+grams(one sentence). Keep name exact; basis MUST be '$basis', never change unit. Add real https source; never invent sources."
        }
        fun create(context: Context, monitor: GoogleAiTrafficMonitor = GoogleAiTrafficMonitor()): GoogleAiNutritionSource {
            // Historical cooldown preferences are deliberately ignored; only per-assessment limits remain.
            val preferences = context.getSharedPreferences("web_ai_rotation", Context.MODE_PRIVATE)
            val previous = WebAiProvider.entries.firstOrNull { it.name == preferences.getString("last_provider", null) }
            val rotation = WebAiRotation(initial = previous, save = {
                preferences.edit().putString("last_provider", it.name).apply()
            })
            return GoogleAiNutritionSource(GoogleAiPageLoader(context), monitor = monitor, rotation = rotation, report = {
                android.util.Log.i("FitAI_Traffic", "provider=${it.lastProvider} requests=${it.requests} minute=${it.requestsInMinute} cache=${it.cacheHits} suppressed=${it.suppressed} outcome=${it.lastOutcome} elapsedMs=${it.lastDurationMs} http=${it.lastHttpStatus}")
            })
        }
    }
}

object GoogleAiNutritionParser {
    private val number = Regex("(?:約|大約|~|～)?\\s*(\\d+(?:\\.\\d+)?)(?:\\s*[–—~～至-]\\s*(\\d+(?:\\.\\d+)?))?")
    private fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).replace(Regex("\\s+"), "")

    fun basisForPortion(portion: String): String? {
        val p = normalized(portion).lowercase()
        if (Regex("^\\d+(?:\\.\\d+)?(g|克|公克|kg|公斤|千克)$").matches(p)) return "100g"
        val unit = Regex("^\\d+(?:\\.\\d+)?(份|個|个|顆|颗|粒|片|碗|包|杯|塊|块|條|条|支|串|盤|盘|盒|罐|瓶)$").matchEntire(p)?.groupValues?.get(1) ?: return null
        return "1" + normalizeServingUnit(unit)
    }

    private fun value(raw: String): Float? {
        val n = number.find(raw) ?: return null
        val lo = n.groupValues[1].toFloatOrNull() ?: return null
        val hi = n.groupValues[2].toFloatOrNull() ?: lo
        return ((lo + hi) / 2f).takeIf { lo.isFinite() && hi.isFinite() && hi >= lo && hi <= 5000 }
    }

    private fun declaresBasis(text: String, basis: String): Boolean {
        val clean = normalized(text).lowercase()
        // Parse the entire quantity instead of matching the trailing "1份" in "11份".
        val boundary = "(?<![\\d.一二三四五六七八九十百千兩两零〇])"
        if (basis == "100g") {
            return Regex("$boundary(?:每)?(\\d+(?:\\.\\d+)?)(?:g|公克|克)(?![a-z])")
                .findAll(clean).any { it.groupValues[1].toFloatOrNull() == 100f }
        }
        val rawUnit = basis.drop(1)
        val unit = if (rawUnit in setOf("顆", "粒")) "[顆颗個个粒]" else Regex.escape(rawUnit)
        return Regex("$boundary(?:每(?:(\\d+(?:\\.\\d+)?|一))?|(\\d+(?:\\.\\d+)?|一))$unit")
            .findAll(clean).toList().let { matches ->
                matches.isNotEmpty() && matches.all {
                    val count = it.groupValues[1].ifBlank { it.groupValues[2] }.ifBlank { "1" }
                    count == "一" || count.toFloatOrNull() == 1f
                }
            }
    }

    data class RecipeDetails(val estimated: Boolean, val assumption: String?)

    fun recipeDetails(page: GoogleAiPage): RecipeDetails {
        val estimated = Regex("(?:類型|类型)\\s*[:：]?\\s*配方估算|(?:^|\\n)\\s*配方估算[：:(（]", RegexOption.IGNORE_CASE)
            .containsMatchIn(page.text)
        val assumption = if (estimated) Regex("假[設设]\\s*[:：]?\\s*([^\\n]{1,500}(?:\\n(?!(?:類型|类型|來源|http))[^\\n]{1,200})?)")
            .find(page.text)?.groupValues?.get(1)
            ?.replace('\n', ' ')
            ?.substringBefore("https://")?.substringBefore("來源")?.substringBefore("AI 可能會出錯")
            ?.trim(' ', '；', ';')?.take(300)
            ?.takeIf { Regex("\\d+(?:\\.\\d+)?\\s*(?:kg|g|公斤|公克|克)", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            else null
        return RecipeDetails(estimated, assumption)
    }

    /** Some public answers render a single CSV or Markdown pipe table as text instead of an HTML table.
     * Accept only an unquoted, complete header/row pair (or explicit 6-column pipe row); ambiguous CSV stays rejected. */
    private fun inlineTextTables(text: String): List<List<List<String>>> {
        val lines = text.lineSequence().take(80).map(String::trim)
            .filter { it.isNotEmpty() && !Regex("^[|｜\\s:+-]+$").matches(it) }.toList()
        val paired = lines.zipWithNext().mapNotNull { (head, row) ->
            if ('"' in head || '"' in row) return@mapNotNull null
            val headers: List<String>
            val values: List<String>
            if (('|' in head || '｜' in head) && ('|' in row || '｜' in row)) {
                headers = head.trim('|', '｜', ' ').split(Regex("[|｜]")).map(String::trim)
                values = row.trim('|', '｜', ' ').split(Regex("[|｜]")).map(String::trim)
            } else {
                headers = head.split(',').map(String::trim)
                values = row.split(',').map(String::trim)
            }
            if (headers.size !in 2..8 || values.size != headers.size) null else listOf(headers, values)
        }
        val singlePipeRows = lines.mapNotNull { line ->
            if ('|' !in line && '｜' !in line) return@mapNotNull null
            val cells = line.trim('|', '｜', ' ').split(Regex("[|｜]")).map(String::trim)
            if (cells.size == 6 &&
                Regex("(?:kcal|大卡|千卡)", RegexOption.IGNORE_CASE).containsMatchIn(cells[2]) &&
                cells.subList(3, 6).all { c -> Regex("(?:公克|克|g)", RegexOption.IGNORE_CASE).containsMatchIn(c) && !c.contains("飽和") && !c.contains("反式") }
            ) {
                listOf(listOf("餐點", "基準", "熱量kcal", "蛋白質g", "碳水g", "脂肪g"), cells)
            } else null
        }
        return paired + singlePipeRows
    }

    private fun hasExplicitBasis(text: String): Boolean =
        Regex("(?:每(?:\\d+(?:\\.\\d+)?|[一二三四五六七八九十百兩两]+)?|\\d+(?:\\.\\d+)?|[一二三四五六七八九十百兩两]+)(?:g|公克|克|份|個|个|顆|颗|粒|片|碗|包|杯|塊|块|條|条|支|串|盤|盘|盒|罐|瓶)")
            .containsMatchIn(normalized(text).lowercase())

    private fun extractProseNutrients(chunk: String): List<Float>? {
        val primary = normalized(chunk)
        if (Regex("比較|对比|對比|對照|相比|相較|versus|\\bvs\\b", RegexOption.IGNORE_CASE).containsMatchIn(primary)) return null
        val energy = Regex("${number.pattern}\\s*(?:大卡|千卡|kcal)", RegexOption.IGNORE_CASE)
            .findAll(primary).toList().singleOrNull()?.let { value(it.value) }
        val labels = listOf("(?:蛋白質|蛋白质)", "(?:碳水化合物(?:[（(]醣類[）)])?|碳水|醣類)",
            "(?<!飽和)(?<!饱和)(?<!反式)(?:總脂肪|总脂肪|脂肪)")
        val macros = labels.map { label ->
            Regex("$label\\s*[：:]?\\s*${number.pattern}\\s*(?:公克|克|g)", RegexOption.IGNORE_CASE)
                .findAll(primary).toList().singleOrNull()?.let { value(it.value) }
        }
        return if (energy != null && macros.all { it != null }) listOf(energy) + macros.filterNotNull() else null
    }

    fun parse(name: String, basis: String, page: GoogleAiPage, sourceUrl: String, provider: WebAiProvider = WebAiProvider.GOOGLE): NutritionFood? {
        if (page.blocked || page.failure != null || page.citations.none { isGoogleCitationUrl(it.url) }) return null
        val recipe = recipeDetails(page)
        if (recipe.estimated && recipe.assumption == null) return null
        val key = WebNutritionScraper.normalizeQuery(name)
        if (!WebNutritionScraper.normalizeQuery(page.text.take(500)).contains(key)) return null
        val cleanText = page.text
            .substringBefore("\n類型").substringBefore("\n类型")
            .substringBefore("類型:").substringBefore("類型：")
            .substringBefore("类型:").substringBefore("类型：")
            .substringBefore("\n假設").substringBefore("\n假设")
            .substringBefore("AI 可能會出錯").substringBefore("如需查詢其他").substringBefore("如果您需要查詢")
            .ifBlank { page.text }
        val intro = cleanText.take(500)
        // Nutrient grams are not declarations of the food's serving basis.
        val nutrientStart = Regex("(?:熱量|卡路里|蛋白質|蛋白质|碳水|醣類|總脂肪|脂肪)(?=\\s*[：:]?\\s*(?:約|大約|~|～)?\\s*\\p{N})").find(intro)?.range?.first ?: intro.length
        val basisIntro = intro.take(nutrientStart)
        val ambiguousBasis = if (basis == "100g") Regex("(?:每\\s*(?:1|一)?|一)\\s*[份個个顆颗粒片碗包杯塊块條条支串盤盘盒罐瓶]").containsMatchIn(basisIntro)
            else Regex("(?<![\\d.])100(?:\\.0+)?(?:g|公克|克)(?![a-z])")
                .containsMatchIn(normalized(basisIntro).lowercase())
        var nutrients: List<Float>? = null
        for (table in page.tables + inlineTextTables(cleanText)) {
            val header = table.firstOrNull()?.map(::normalized) ?: continue
            // Google also renders vertical nutrient tables with one column per serving basis.
            val basisColumn = header.indexOfFirst { declaresBasis(it, basis) }.takeIf { it > 0 }
                ?: if (header.size == 2 && !hasExplicitBasis(header[1]) && declaresBasis(basisIntro, basis) && !ambiguousBasis) 1 else -1
            if (basisColumn > 0) {
                val nutrientLabels = listOf(setOf("熱量", "卡路里", "熱量(kcal)"), setOf("蛋白質", "蛋白质", "蛋白質(g)"),
                    setOf("碳水化合物", "碳水", "醣類", "碳水化合物(g)"), setOf("脂肪", "總脂肪", "脂肪(g)"))
                val values = nutrientLabels.map { names ->
                    table.drop(1).firstOrNull { row -> row.isNotEmpty() && normalized(row[0]) in names }
                        ?.getOrNull(basisColumn)?.let(::value)
                }
                if (values.all { it != null }) { nutrients = values.filterNotNull(); break }
            }
            fun column(vararg labels: String) = header.indexOfFirst { h -> labels.any(h::contains) }
            val columns = listOf(column("熱量", "卡路里", "kcal"), column("蛋白質", "蛋白质"), column("碳水", "醣類"),
                header.indexOfFirst { (it.contains("脂肪") && !it.contains("飽和") && !it.contains("反式")) })
            if (columns.any { it < 0 }) continue
            val dataRows = table.drop(1)
            val row = dataRows.firstOrNull { cells -> cells.any { WebNutritionScraper.normalizeQuery(it) == key } }
                ?: dataRows.singleOrNull()?.takeIf { cells ->
                    cells.any {
                        val normCell = WebNutritionScraper.normalizeQuery(it).replace(Regex("[（(].*?[）)]"), "")
                        normCell == key
                    }
                } ?: continue
            val unitColumn = column("基準", "基准", "份量", "食用量")
            val unitText = if (unitColumn in row.indices) row[unitColumn] else header.joinToString(" ") + basisIntro
            if (!declaresBasis(unitText, basis)) continue
            val weightColumn = column("重量")
            if (basis == "100g" && weightColumn in row.indices && value(row[weightColumn]) != 100f) continue
            if (columns.any { it !in row.indices }) continue
            val values = columns.map { value(row[it]) }
            if (values.all { it != null }) { nutrients = values.filterNotNull(); break }
        }
        if (nutrients == null && declaresBasis(basisIntro, basis) && !ambiguousBasis) {
            // Google sometimes renders each digit/unit in a separate block (1\n6\n7\nk\nc\na\nl).
            val primaryChunk = cleanText.substringBefore("比較表").take(900)
            nutrients = extractProseNutrients(primaryChunk)
                ?: primaryChunk.lineSequence().firstOrNull { it.isNotBlank() && WebNutritionScraper.normalizeQuery(it).contains(key) }
                    ?.let(::extractProseNutrients)
        }
        val values = nutrients ?: return null
        val energy = values[1] * 4 + values[2] * 4 + values[3] * 9
        if (values[0] > 20 && energy !in values[0] * 0.5f..values[0] * 1.8f) return null
        if (values[0] <= 20 && energy > 50) return null
        val categoryLabel = when {
            provider in setOf(WebAiProvider.DUCK, WebAiProvider.GEMINI, WebAiProvider.CHATGPT) ->
                "${provider.label} / ${page.modelLabel?.takeIf { it.isNotBlank() } ?: "模型未知"}"
            else -> provider.label
        }.take(40)
        val cleanSourceUrl = when (provider) {
            WebAiProvider.DUCK -> "https://duck.ai/"
            WebAiProvider.GEMINI -> "https://gemini.google.com/app"
            WebAiProvider.CHATGPT -> "https://chatgpt.com/"
            else -> sourceUrl
        }
        return NutritionFood(provider.foodId, name.trim(), "",
            (if (recipe.estimated) "【${provider.label} 配方估算】每$basis；假設：${recipe.assumption}。非完整營養標示。" else
                "【${provider.label} 估算】每$basis；非官方營養標示。來源若提供區間，顯示區間中值；實際配方與份量可能不同。").take(400),
            values[0], values[1], values[2], values[3], categoryLabel,
            cleanSourceUrl, servingLabel = basis).takeIf { it.isValid() }
    }
}
