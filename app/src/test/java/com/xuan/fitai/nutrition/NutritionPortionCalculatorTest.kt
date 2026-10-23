package com.xuan.fitai.nutrition

import com.xuan.fitai.ai.GemmaFoodAnalysis
import org.junit.Assert.*
import org.junit.Test

class NutritionPortionCalculatorTest {
    private val rice = NutritionFood("A", "白飯", "", "熟", 183f, 3.1f, 41f, .3f)
    private val beef = NutritionFood("B", "牛肉", "", "生", 233f, 16.9f, 0f, 17.9f)
    private val analysis = GemmaFoodAnalysis(999f, 99f, 99f, 99f, true, "建議", "錯誤的模型算式", referenceFoodId = "A")

    @Test fun explicitGramsOverrideWrongModelMathWithoutLosingDecimalMacros() {
        val result = NutritionPortionCalculator.apply(analysis, NutritionReference(1, true, listOf(rice)), "200g")
        assertEquals(366f, result.calories); assertEquals(6.2f, result.protein); assertEquals(.6f, result.fat)
        assertFalse(result.reasoning.contains("錯誤"))
    }
    @Test fun selectedIdDeterminesValuesAndUnknownIdHasExplicitFallback() {
        val ref = NutritionReference(1, false, listOf(rice, beef))
        assertEquals(466f, NutritionPortionCalculator.apply(analysis.copy(referenceFoodId = "B"), ref, "0.2公斤").calories)
        val fallback = NutritionPortionCalculator.apply(analysis.copy(referenceFoodId = "invented"), ref, "200 g")
        assertEquals(366f, fallback.calories); assertTrue(fallback.reasoning.contains("未指定有效樣品"))
    }
    @Test fun ambiguousServingAndMissingReferenceKeepOriginalEstimation() {
        assertEquals(analysis, NutritionPortionCalculator.apply(analysis, NutritionReference(1, true, listOf(rice)), "1碗"))
        assertEquals(analysis, NutritionPortionCalculator.apply(analysis, null, "200g"))
    }
}
