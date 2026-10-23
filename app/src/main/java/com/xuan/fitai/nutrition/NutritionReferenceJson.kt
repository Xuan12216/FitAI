package com.xuan.fitai.nutrition

import org.json.JSONArray
import org.json.JSONObject

object NutritionReferenceJson {
    fun foodToJson(food: NutritionFood): JSONObject = JSONObject().put("id", food.id).put("name", food.name)
        .put("aliases", food.aliases).put("description", food.description).put("calories", food.calories)
        .put("protein", food.protein).put("carbs", food.carbs).put("fat", food.fat).put("category", food.category)

    fun foodFromJson(o: JSONObject): NutritionFood = NutritionFood(
        o.getString("id"), o.getString("name"), o.getString("aliases"), o.getString("description"),
        o.getDouble("calories").toFloat(), o.getDouble("protein").toFloat(), o.getDouble("carbs").toFloat(), o.getDouble("fat").toFloat(), o.optString("category")
    ).also { require(it.isValid()) { "營養來源資料格式錯誤" } }

    fun encode(reference: NutritionReference): String = JSONObject().put("provider", "tfda")
        .put("fetchedAt", reference.fetchedAt).put("fromCache", reference.fromCache)
        .put("selectedFoodId", reference.selectedFoodId ?: JSONObject.NULL)
        .put("foods", JSONArray(reference.foods.map(::foodToJson))).toString()

    fun decode(raw: String): NutritionReference {
        require(raw.length <= 8000)
        val o = JSONObject(raw)
        require(o.getString("provider") == "tfda")
        val time = o.getLong("fetchedAt"); require(time > 0)
        val a = o.getJSONArray("foods"); require(a.length() in 1..5)
        val foods = (0 until a.length()).map { foodFromJson(a.getJSONObject(it)) }
        val selected = o.optString("selectedFoodId").takeIf { value -> foods.any { it.id == value } }
        return NutritionReference(time, o.getBoolean("fromCache"), foods, selected)
    }

    fun decodeOrNull(raw: String?): NutritionReference? = raw?.let { runCatching { decode(it) }.getOrNull() }
    fun withSelection(raw: String?, id: String?): String? = decodeOrNull(raw)?.let { ref ->
        encode(ref.copy(selectedFoodId = id?.takeIf { value -> ref.foods.any { it.id == value } }))
    }
}
