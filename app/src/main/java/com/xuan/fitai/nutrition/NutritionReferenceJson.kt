package com.xuan.fitai.nutrition

import org.json.JSONArray
import org.json.JSONObject

object NutritionReferenceJson {
    fun foodToJson(food: NutritionFood): JSONObject = JSONObject().put("id", food.id).put("name", food.name)
        .put("aliases", food.aliases).put("description", food.description).put("calories", food.calories)
        .put("protein", food.protein).put("carbs", food.carbs).put("fat", food.fat).put("category", food.category)
        .put("sourceUrl", food.sourceUrl ?: JSONObject.NULL)
        .put("servingLabel", food.servingLabel).put("hasMacros", food.hasMacros)

    fun foodFromJson(o: JSONObject): NutritionFood = NutritionFood(
        o.getString("id"), o.getString("name"), o.getString("aliases"), o.getString("description"),
        o.getDouble("calories").toFloat(), o.getDouble("protein").toFloat(), o.getDouble("carbs").toFloat(), o.getDouble("fat").toFloat(), o.optString("category"),
        if (o.isNull("sourceUrl")) null else o.optString("sourceUrl").takeIf { it.isNotBlank() },
        o.optString("servingLabel", "100g"), o.optBoolean("hasMacros", true)
    ).also { require(it.isValid()) { "營養來源資料格式錯誤" } }

    fun encode(reference: NutritionReference): String = JSONObject().put("provider", reference.foods.firstNotNullOfOrNull { it.webAiProvider?.name?.lowercase() }?.let { "$it-ai" } ?: "tfda")
        .put("fetchedAt", reference.fetchedAt).put("fromCache", reference.fromCache)
        .put("selectedFoodId", reference.selectedFoodId ?: JSONObject.NULL)
        .put("googleAiAnswer", reference.googleAiAnswer ?: JSONObject.NULL)
        .put("recipeEstimate", reference.recipeEstimate).put("servingAssumption", reference.servingAssumption ?: JSONObject.NULL)
        .put("googleAiCitations", JSONArray(reference.googleAiCitations.map { JSONObject().put("title", it.title).put("url", it.url) }))
        .put("foods", JSONArray(reference.foods.map(::foodToJson))).toString()

    fun decode(raw: String): NutritionReference {
        require(raw.length <= 8000)
        val o = JSONObject(raw)
        val validProviders = setOf("tfda") + WebAiProvider.entries.map { "${it.name.lowercase()}-ai" }
        require(o.getString("provider") in validProviders)
        val time = o.getLong("fetchedAt"); require(time > 0)
        val a = o.getJSONArray("foods"); require(a.length() in 1..5)
        val foods = (0 until a.length()).map { foodFromJson(a.getJSONObject(it)) }
        val selected = o.optString("selectedFoodId").takeIf { value -> foods.any { it.id == value } }
        val answer = if (o.isNull("googleAiAnswer")) null else o.optString("googleAiAnswer").takeIf { it.isNotBlank() }
        require(answer == null || answer.length <= 1800)
        val links = o.optJSONArray("googleAiCitations") ?: JSONArray()
        require(links.length() <= 2)
        val citations = (0 until links.length()).map { links.getJSONObject(it) }.map {
            GoogleAiCitation(it.getString("title"), it.getString("url")).also { c ->
                require(c.title.length <= 100 && c.url.length <= 700 && isGoogleCitationUrl(c.url))
            }
        }
        val assumption = if (o.isNull("servingAssumption")) null else o.optString("servingAssumption").takeIf { it.isNotBlank() }
        require(assumption == null || assumption.length <= 300)
        val recipe = o.optBoolean("recipeEstimate", false)
        require(!recipe || (assumption != null && foods.any { it.webAiProvider != null }))
        return NutritionReference(time, o.getBoolean("fromCache"), foods, selected, answer, citations, recipe, assumption)
    }

    fun decodeOrNull(raw: String?): NutritionReference? = raw?.let { runCatching { decode(it) }.getOrNull() }
    fun withSelection(raw: String?, id: String?): String? = decodeOrNull(raw)?.let { ref ->
        encode(ref.copy(selectedFoodId = id?.takeIf { value -> ref.foods.any { it.id == value } }))
    }
}
