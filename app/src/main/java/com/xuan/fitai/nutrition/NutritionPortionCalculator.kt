package com.xuan.fitai.nutrition

import com.xuan.fitai.ai.GemmaFoodAnalysis
import java.text.Normalizer
import java.util.Locale

object NutritionPortionCalculator {
    /** Scale only compatible declared units; never invent the mass of a published serving. */
    fun apply(analysis: GemmaFoodAnalysis, reference: NutritionReference?, portion: String): GemmaFoodAnalysis {
        if (reference == null || reference.foods.isEmpty()) return analysis
        val recipeNote = if (reference.recipeEstimate) "配方估算（非完整營養標示）；每${reference.foods.first().servingLabel}的假設：${reference.servingAssumption}。" else ""
        val chosen = reference.foods.firstOrNull { it.id == analysis.referenceFoodId }
        val food = chosen ?: reference.foods.first()
        val normalizedPortion = Normalizer.normalize(portion.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        if (!food.isPer100g) {
            val serving = Regex("^(\\d+(?:\\.\\d+)?)\\s*(份|个|個|颗|顆|粒|片|碗|包|杯|块|塊|条|條|支|串|盘|盤|盒|罐|瓶)$").matchEntire(normalizedPortion) ?: return analysis
            if (normalizeServingUnit(serving.groupValues[2]) != food.servingLabel.drop(1)) return analysis
            val count = serving.groupValues[1].toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..100f } ?: return analysis
            return analysis.copy(
                calories = food.calories * count,
                protein = if (food.hasMacros) food.protein * count else analysis.protein,
                carbs = if (food.hasMacros) food.carbs * count else analysis.carbs,
                fat = if (food.hasMacros) food.fat * count else analysis.fat,
                referenceFoodId = food.id,
                reasoning = recipeNote + (if (food.webAiProvider != null) "${food.category} 估算，非官方營養標示；區間取中值。" else "") + "參考「${food.name}」每${food.servingLabel} × $count；${if (reference.recipeEstimate) "依相同份數換算" else "來源未標克重"}，未換算每100克。" +
                    (if (!food.hasMacros) "來源僅提供熱量，蛋白質、碳水與脂肪仍為 AI 估算。" else "") +
                    (if (chosen == null && reference.foods.size > 1) "模型未指定有效樣品，暫採第一筆，請確認。" else "") +
                    "實際份量與配方可能不同。",
                thinking = null
            )
        }
        val mass = Regex("^(\\d+(?:\\.\\d+)?)\\s*(g|克|公克|kg|公斤|千克)$").matchEntire(
            normalizedPortion
        ) ?: return analysis
        val grams = mass.groupValues[1].toFloatOrNull()?.times(if (mass.groupValues[2] in listOf("kg", "公斤", "千克")) 1000 else 1)
            ?.takeIf { it.isFinite() && it in 0f..10000f } ?: return analysis
        val scale = grams / 100f
        val assumption = if (reference.foods.size > 1 && chosen == null) "模型未指定有效樣品，暫採第一筆「${food.name}」，請確認部位與料理方式。" else "參考「${food.name}」，請確認部位與料理方式。"
        return analysis.copy(
            calories = food.calories * scale, protein = food.protein * scale, carbs = food.carbs * scale, fat = food.fat * scale,
            referenceFoodId = food.id,
            reasoning = recipeNote + "${if (food.webAiProvider != null) "${food.category} 估算，非官方營養標示；區間取中值。" else if (food.id.startsWith("TW-")) "此為內建近似配方，並非官方或品牌標示。" else ""}${assumption}以每 100 克數值 × ${String.format(Locale.ROOT, "%.2f", scale)} 換算 ${grams} 克，由程式計算；個人目標不改變營養數值。",
            thinking = null
        )
    }
}
