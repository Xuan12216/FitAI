package com.xuan.fitai.nutrition

import com.xuan.fitai.ai.GemmaFoodAnalysis
import java.text.Normalizer
import java.util.Locale

object NutritionPortionCalculator {
    /** Only explicit edible mass is deterministic; 1 bowl/serving is still an estimate. */
    fun apply(analysis: GemmaFoodAnalysis, reference: NutritionReference?, portion: String): GemmaFoodAnalysis {
        if (reference == null || reference.foods.isEmpty()) return analysis
        val mass = Regex("^(\\d+(?:\\.\\d+)?)\\s*(g|克|公克|kg|公斤|千克)$").matchEntire(
            Normalizer.normalize(portion.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        ) ?: return analysis
        val grams = mass.groupValues[1].toFloatOrNull()?.times(if (mass.groupValues[2] in listOf("kg", "公斤", "千克")) 1000 else 1)
            ?.takeIf { it.isFinite() && it in 0f..10000f } ?: return analysis
        val chosen = reference.foods.firstOrNull { it.id == analysis.referenceFoodId }
        val food = chosen ?: reference.foods.first()
        val scale = grams / 100f
        val assumption = if (reference.foods.size > 1 && chosen == null) "模型未指定有效樣品，暫採第一筆「${food.name}」，請確認部位與料理方式。" else "參考「${food.name}」，請確認部位與料理方式。"
        return analysis.copy(
            calories = food.calories * scale, protein = food.protein * scale, carbs = food.carbs * scale, fat = food.fat * scale,
            referenceFoodId = food.id,
            reasoning = "${assumption}以每 100 克數值 × ${String.format(Locale.ROOT, "%.2f", scale)} 換算 ${grams} 克，由程式計算；個人目標不改變營養數值。",
            thinking = null
        )
    }
}
