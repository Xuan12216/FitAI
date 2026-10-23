package com.xuan.fitai.nutrition

import java.text.Normalizer
import java.util.Locale

const val TFDA_SOURCE_URL = "https://data.gov.tw/dataset/8543"
const val TFDA_DOWNLOAD_URL = "https://data.fda.gov.tw/data/opendata/export/20/old-json"

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
) {
    fun isValid() = id.length in 1..40 && name.length in 1..100 && aliases.length <= 500 && description.length <= 400 && category.length <= 40 &&
        listOf(calories, protein, carbs, fat).all { it.isFinite() && it >= 0 } &&
        calories <= 1000 && listOf(protein, carbs, fat).all { it <= 100 } && protein + carbs + fat <= 105
}

/** Metadata comes from the app's lookup, never from a model's claims about browsing. */
data class NutritionReference(val fetchedAt: Long, val fromCache: Boolean, val foods: List<NutritionFood>, val selectedFoodId: String? = null) {
    fun promptText(): String = foods.joinToString("\n") {
        "id=${it.id} ${it.name} [${it.description.take(70)}]: per 100g edible portion, ${it.calories} kcal, protein ${it.protein}g, carbs ${it.carbs}g, fat ${it.fat}g."
    }
}

data class NutritionLookup(val reference: NutritionReference? = null, val message: String)

fun interface NutritionLookupSource {
    suspend fun lookup(foodName: String): NutritionLookup
}

object NutritionMatcher {
    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace("臺", "台").replace(Regex("\\s+"), "")

    fun find(query: String, foods: List<NutritionFood>): List<NutritionFood> {
        val key = normalize(query)
        if (key.length !in 2..100) return emptyList()
        // A query may match a more specific dish, never the reverse (牛肉麵 -> 牛肉).
        val animal = Regex("^([牛豬羊雞鴨鵝])肉$").matchEntire(key)?.groupValues?.get(1)
        return foods.mapNotNull { food ->
            // A broad meat query must not prefer 牛肉醬、牛肉餡餅 or 蝸牛肉 over actual cuts.
            if (animal != null && food.category != "肉類") return@mapNotNull null
            val score = when {
                normalize(food.name) == key -> 100
                food.aliases.split(',', '，', '、', '(', ')', '（', '）').any { normalize(it) == key } ->
                    if (food.name.endsWith("平均值")) 90 else 80
                normalize(food.name).contains(key) || food.aliases.split(',', '，', '、', '(', ')', '（', '）').any { normalize(it).contains(key) } -> 60
                animal != null && food.category == "肉類" && food.name.contains(animal) -> 50
                else -> return@mapNotNull null
            }
            food to score
        }.sortedWith(compareByDescending<Pair<NutritionFood, Int>> { it.second }.thenBy { it.first.id })
            .let { matches ->
                // Prefer an exact sample or a published average; otherwise present several candidates.
                val best = matches.firstOrNull()?.second ?: return emptyList()
                val candidates = if (best >= 90) matches.filter { it.second == best }.take(1) else matches
                candidates.distinctBy { normalize(it.first.name).replace(Regex("[（(].*?[）)]"), "") }
                    .take(5).map { it.first }
            }
    }
}
