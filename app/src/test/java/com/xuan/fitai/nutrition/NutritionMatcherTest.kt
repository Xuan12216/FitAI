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

    @Test fun taiwaneseCommonFoodsAreAllValidAndMatchEverydayMeals() {
        assertTrue(TaiwaneseCommonFoods.ALL.all { it.isValid() })
        assertTrue(NutritionMatcher.find("牛肉麵", TaiwaneseCommonFoods.ALL).isNotEmpty())
        assertTrue(NutritionMatcher.find("起司蛋餅", TaiwaneseCommonFoods.ALL).isNotEmpty())
        assertTrue(NutritionMatcher.find("滷肉飯", TaiwaneseCommonFoods.ALL).isNotEmpty())
        assertTrue(NutritionMatcher.find("雞腿便當", TaiwaneseCommonFoods.ALL).isNotEmpty())
    }

    @Test fun parentheticalModifierMatchesButCookedFoodDoesNotBecomeRawMeat() {
        val tfdaSamples = listOf(food("1", "豆漿(無糖)"), food("2", "去皮清肉(肉雞)", "去皮雞胸肉"))
        assertEquals(listOf("1"), NutritionMatcher.find("無糖豆漿", tfdaSamples).map { it.id })
        assertTrue(NutritionMatcher.find("無糖豆漿", listOf(food("3", "豆漿(無糖)巧克力"))).isEmpty())
        assertTrue(NutritionMatcher.find("水煮雞胸肉", tfdaSamples).isEmpty())
    }

    @Test fun distinctFoodsAndFormulationsAreNotAliases() {
        for (query in listOf("麥香魚", "麥香雞", "低脂鮮奶", "無糖鮮奶茶", "滷海帶", "清蒸臭豆腐", "燒餅夾蛋", "蚵仔麵線")) {
            assertTrue("Incorrect built-in match for $query", NutritionMatcher.find(query, TaiwaneseCommonFoods.ALL).isEmpty())
        }
        assertEquals("滷肉飯", NutritionMatcher.find("魯肉飯", TaiwaneseCommonFoods.ALL).first().name)
        assertEquals("起司蛋餅", NutritionMatcher.find("起士蛋餅", TaiwaneseCommonFoods.ALL).first().name)
    }

    @Test fun taiwanBreakfastMatchesHamPancakeAndKeepsUnpublishedMacrosUnknown() {
        // Same row structure and values as NTU's public breakfast table (2026-10-23).
        val html = """<table><tbody>
            <tr><td>火腿蛋餅（Chinese omelet with ham）</td><td>281&nbsp;</td><td>&nbsp;</td></tr>
            <tr><td>原味蛋餅（Original chinese omelet）</td><td>236&nbsp;</td><td>&nbsp;</td></tr>
            <tr><td>火腿蛋三明治（Ham and egg sandwich）</td><td>472&nbsp;</td><td>&nbsp;</td></tr>
            </tbody></table>"""
        val food = WebNutritionScraper.parseTaiwanMealHtml("火腿蛋餅", html, NTU_BREAKFAST_URL).single()
        assertEquals("火腿蛋餅", food.name); assertEquals(281f, food.calories)
        assertEquals("1份", food.servingLabel); assertFalse(food.hasMacros)
        assertEquals(NTU_BREAKFAST_URL, food.sourceUrl)
        assertTrue(NutritionReference(1, false, listOf(food)).promptText().contains("NOT published"))
        assertTrue(WebNutritionScraper.parseTaiwanMealHtml("麥當勞火腿蛋餅", html, NTU_BREAKFAST_URL).isEmpty())
        assertTrue(WebNutritionScraper.parseTaiwanMealHtml("火腿蛋堡", html, NTU_BREAKFAST_URL).isEmpty())
    }

    @Test fun taiwanMealLookupPreservesModifiersAndRejectsAmbiguousOrDifferentFoods() {
        fun row(name: String, calories: String = "300") = "<tr><td>$name</td><td>$calories</td><td></td></tr>"
        val breakfast = NTU_BREAKFAST_URL
        assertTrue(WebNutritionScraper.parseTaiwanMealHtml("火腿蛋餅", row("火腿蛋堡"), breakfast).isEmpty())
        assertTrue(WebNutritionScraper.parseTaiwanMealHtml("起司蛋餅", row("蛋餅"), breakfast).isEmpty())
        assertTrue(WebNutritionScraper.parseTaiwanMealHtml("無糖豆漿", row("豆漿"), breakfast).isEmpty())
        assertTrue(WebNutritionScraper.parseTaiwanMealHtml("豆漿", row("豆漿(無糖)"), breakfast).isEmpty())
        assertTrue(WebNutritionScraper.parseTaiwanMealHtml("草莓烤吐司", row("草莓烤吐司", "126 / 244"), breakfast).isEmpty())
        assertEquals(1, WebNutritionScraper.parseTaiwanMealHtml("起士蛋餅", row("起司蛋餅"), breakfast).size)
        assertEquals(1, WebNutritionScraper.parseTaiwanMealHtml("牛肉麵", row("牛肉麵(Beef soup noodles)", "580"), "https://meals.ntu.edu.tw/CampusDining/kcal/kcal").size)
        assertTrue(WebNutritionScraper.parseTaiwanMealHtml("火腿蛋餅", row("火腿蛋餅"), "https://other.example/table").isEmpty())
    }

    @Test fun sourceRoutingOnlyUsesTaiwanPublicPagesAndSkipsPlainIngredients() {
        assertEquals(NTU_BREAKFAST_URL, WebNutritionScraper.sourcePageUrl("火腿蛋餅"))
        assertEquals("https://meals.ntu.edu.tw/CampusDining/kcal/kcal", WebNutritionScraper.sourcePageUrl("牛肉麵"))
        assertEquals("https://meals.ntu.edu.tw/CampusDining/kcal/rice", WebNutritionScraper.sourcePageUrl("滷肉飯"))
        assertNull(WebNutritionScraper.sourcePageUrl("牛肉"))
        assertNull(WebNutritionScraper.sourcePageUrl("雞胸肉"))
        assertTrue(NTU_MEAL_SOURCE_URLS.all { isNutritionSourceUrl(it) })
    }
}
