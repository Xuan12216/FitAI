package com.xuan.fitai.nutrition

import java.text.Normalizer
import java.util.Locale

const val TFDA_SOURCE_URL = "https://data.gov.tw/dataset/8543"
const val TFDA_DOWNLOAD_URL = "https://data.fda.gov.tw/data/opendata/export/20/old-json"
const val NTU_BREAKFAST_URL = "https://meals.ntu.edu.tw/CampusDining/kcal/breakfast"
val NTU_MEAL_SOURCE_URLS = setOf("breakfast", "kcal2", "kcal", "rice", "soup")
    .map { "https://meals.ntu.edu.tw/CampusDining/kcal/$it" }.toSet()

data class NutritionFood(
    val id: String,
    val name: String,
    val aliases: String,
    val description: String,
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val category: String = "",
    val sourceUrl: String? = null,
    val servingLabel: String = "100g",
    val hasMacros: Boolean = true,
) {
    val isPer100g: Boolean get() = servingLabel == "100g"
    fun isValid() = id.length in 1..40 && name.length in 1..100 && aliases.length <= 500 && description.length <= 400 && category.length <= 40 &&
        servingLabel in setOf("100g", "1份", "1個", "1片", "1碗", "1包", "1杯") &&
        listOf(calories, protein, carbs, fat).all { it.isFinite() && it >= 0 } &&
        calories <= (if (isPer100g) 1000 else 5000) &&
        listOf(protein, carbs, fat).all { it <= if (isPer100g) 100 else 500 } &&
        protein + carbs + fat <= (if (isPer100g) 105 else 600) &&
        (hasMacros || (protein == 0f && carbs == 0f && fat == 0f)) &&
        (sourceUrl == null || isNutritionSourceUrl(sourceUrl))
}

fun isNutritionSourceUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    value.length <= (if (WebAiProvider.fromUrl(value) != null) 1800 else 512) && uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
        // Legacy source URLs remain readable in existing records; new requests use Taiwan sources.
        (uri.host in setOf("www.fatsecret.cn", "fatsecret.cn") || value in NTU_MEAL_SOURCE_URLS || WebAiProvider.fromUrl(value) != null)
}.getOrDefault(false)

fun isGoogleAiSearchUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    uri.scheme == "https" && uri.host == "www.google.com" && uri.path == "/search" && uri.userInfo == null && uri.port == -1 &&
        uri.rawQuery.orEmpty().split('&').contains("udm=50") && value.length <= 1500
}.getOrDefault(false)

/** Metadata comes from the app's lookup, never from a model's claims about browsing. */
data class NutritionReference(val fetchedAt: Long, val fromCache: Boolean, val foods: List<NutritionFood>, val selectedFoodId: String? = null,
    val googleAiAnswer: String? = null, val googleAiCitations: List<GoogleAiCitation> = emptyList(),
    val recipeEstimate: Boolean = false, val servingAssumption: String? = null) {
    fun promptText(): String = foods.joinToString("\n") {
        "id=${it.id} ${it.name} [${if (it.id.startsWith("TW-")) "APPROXIMATE recipe, not verified brand data; " else ""}${it.description.take(70)}]: ${if (it.isPer100g) "per 100g edible portion" else "per ${it.servingLabel}; mass UNKNOWN, never treat as 100g"}, ${it.calories} kcal; ${if (it.hasMacros) "protein ${it.protein}g, carbs ${it.carbs}g, fat ${it.fat}g" else "macros NOT published; estimate separately, NOT zero"}."
    }
}

data class NutritionLookup(val reference: NutritionReference? = null, val message: String)

fun interface NutritionLookupSource {
    suspend fun lookup(foodName: String): NutritionLookup
}

object NutritionMatcher {
    private val whitespace = Regex("\\s+")
    private val parenthetical = Regex("^([^（(]+)[（(]([^）)]+)[）)]$")
    private val sampleQualifier = Regex("[（(].*?[）)]")

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace("臺", "台").replace(whitespace, "")

    /** Expands "豆漿(無糖)" into ["豆漿(無糖)", "豆漿無糖", "無糖豆漿"] so natural word order matches. */
    private fun nameVariants(raw: String): List<String> {
        val norm = normalize(raw)
        val match = parenthetical.matchEntire(norm) ?: return listOf(norm)
        val base = match.groupValues[1]
        val mod = match.groupValues[2]
        return listOf(norm, base + mod, mod + base)
    }

    fun find(query: String, foods: List<NutritionFood>): List<NutritionFood> {
        val key = normalize(query)
        if (key.length !in 2..100) return emptyList()
        // A query may match a more specific dish, never the reverse (牛肉麵 -> 牛肉).
        val animal = Regex("^([牛豬羊雞鴨鵝])肉$").matchEntire(key)?.groupValues?.get(1)
        return foods.mapNotNull { food ->
            // A broad meat query must not prefer 牛肉醬、牛肉餡餅 or 蝸牛肉 over actual cuts.
            if (animal != null && food.category != "肉類") return@mapNotNull null
            val variants = nameVariants(food.name)
            val aliasTokens = food.aliases.split(',', '，', '、', '(', ')', '（', '）')
                .map { normalize(it) }
                .filter { it.isNotEmpty() }
            val score = when {
                variants.first() == key -> 100
                key in variants -> 95
                aliasTokens.any { it == key } ->
                    if (food.name.endsWith("平均值")) 90 else 85
                variants.any { it.contains(key) } || aliasTokens.any { it.contains(key) } -> 60
                animal != null && food.category == "肉類" && food.name.contains(animal) -> 50
                else -> return@mapNotNull null
            }
            food to score
        }.sortedWith(compareByDescending<Pair<NutritionFood, Int>> { it.second }.thenBy { it.first.id })
            .let { matches ->
                // Prefer an exact sample or a published average; otherwise present several candidates.
                val best = matches.firstOrNull()?.second ?: return emptyList()
                val candidates = if (best >= 90) matches.filter { it.second == best }.take(1) else matches
                candidates.distinctBy { normalize(it.first.name).replace(sampleQualifier, "") }
                    .take(5).map { it.first }
            }
    }
}
