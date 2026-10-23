package com.xuan.fitai.nutrition

import org.junit.Assert.*
import org.junit.Test

class NutritionMatcherTest {
    private fun food(id: String, name: String, aliases: String = "") = NutritionFood(id, name, aliases, "熟", 100f, 2f, 20f, 1f)

    @Test fun mixedDishNeverMatchesAnIngredient() {
        assertTrue(NutritionMatcher.find("牛肉麵", listOf(food("1", "牛肉"), food("2", "麵"))).isEmpty())
        assertTrue(NutritionMatcher.find("炸雞胸肉", listOf(food("1", "雞胸肉"))).isEmpty())
    }
    @Test fun wholeAliasAndPublishedAverageHavePriority() {
        val foods = listOf(food("1", "紅皮蕉", "香蕉,芎蕉"), food("2", "北蕉平均值", "香蕉,芎蕉"))
        assertEquals(listOf("2"), NutritionMatcher.find("香蕉", foods).map { it.id })
        assertEquals(listOf("1"), NutritionMatcher.find("紅皮蕉", foods).map { it.id })
    }
    @Test fun ambiguousAliasesAreBoundedAndKeepPreparationVisible() {
        val foods = (1..8).map { food("$it", "不同樣品$it", "豆腐") }
        assertEquals(5, NutritionMatcher.find("豆腐", foods).size)
        assertTrue(NutritionReference(1, false, foods.take(3)).promptText().contains("per 100g edible portion"))
    }
    @Test fun broadMeatQueryIncludesCutsButNotMilk() {
        val cuts = listOf(food("1", "牛後腿肉").copy(category = "肉類"), food("2", "牛肩胛小排").copy(category = "肉類"), food("3", "牛乳").copy(category = "乳品類"), food("4", "牛肉醬").copy(category = "調味料及香辛料類"), food("5", "蝸牛肉").copy(category = "魚貝類"))
        assertEquals(listOf("1", "2"), NutritionMatcher.find("牛肉", cuts).map { it.id })
    }
    @Test fun referenceShrinksToContextBudgetAndKeepsAnswerSpace() {
        val rows = (1..5).map { food("$it", "牛肉樣品$it").copy(description = "樣品狀態:生; 不同部位的牛肉，尚未烹煮，請依實際重量換算。".repeat(3)) }
        val reference = NutritionReference(1000, false, rows)
        val large = FoodAssessment.fitReference(reference, "牛肉", "200g", "維持", 4096)
        val small = FoodAssessment.fitReference(reference, "牛肉", "200g", "維持", 1024)
        assertEquals(5, large?.foods?.size)
        assertTrue((small?.foods?.size ?: 0) < 5)
        if (small != null) assertTrue(com.xuan.fitai.ai.PromptBuilder.estimateTokenCount(com.xuan.fitai.ai.PromptBuilder.buildFoodAnalysisPrompt("牛肉", "200g", "維持", 1024, small.promptText())) + 256 <= 1024)
    }
    @Test fun numericValidationRejectsMissingOrImpossibleNutrients() {
        assertFalse(food("1", "測試").copy(calories = Float.NaN).isValid())
        assertFalse(food("1", "測試").copy(protein = 101f).isValid())
        assertFalse(food("1", "測試").copy(carbs = -1f).isValid())
        assertTrue(food("1", "測試").isValid())
    }
}
