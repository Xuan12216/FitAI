package com.xuan.fitai.nutrition

import com.xuan.fitai.ai.GemmaFoodAnalysis
import org.junit.Assert.*
import org.junit.Test

class WebNutritionScraperRegressionTest {
    private fun html(title: String = "火腿蛋餅", serving: String = "1份") = """
        <td class="borderBottom"><a class="prominent">$title</a>
        <div class="smallText greyText greyLink">每 $serving - 卡路里: 200 千卡 | 脂肪: 9 克 | 碳水物: 15 克 | 蛋白质: 10 克</div></td>
    """.trimIndent()

    @Test fun unknownServingMassIsNeverTreatedAs100Grams() {
        val food = WebNutritionScraper.parseFatSecretHtml("火腿蛋餅", html()).single()
        assertEquals("1份", food.servingLabel)
        assertEquals(200f, food.calories)
        val reference = NutritionReference(1000, false, listOf(food))
        val estimate = GemmaFoodAnalysis(123f, 1f, 2f, 3f, true, "", "")
        assertEquals(estimate, NutritionPortionCalculator.apply(estimate, reference, "200g"))
        assertEquals(400f, NutritionPortionCalculator.apply(estimate, reference, "2份").calories)
    }

    @Test fun explicitMassAndCountAreScaledUsingTheirOwnUnits() {
        val per100 = WebNutritionScraper.parseFatSecretHtml("火腿蛋餅", html(serving = "1份（50克）")).single()
        assertEquals("100g", per100.servingLabel)
        assertEquals(400f, per100.calories)
        val perPiece = WebNutritionScraper.parseFatSecretHtml("火腿蛋餅", html(serving = "2个")).single()
        assertEquals("1個", perPiece.servingLabel)
        assertEquals(100f, perPiece.calories)
    }

    @Test fun ambiguousOrInvalidServingDoesNotCreateA100GramRecord() {
        for (serving in listOf("小份", "0份", "0克", "1份（50克至100克）", "50至100克", "50～100克")) {
            assertTrue(serving, WebNutritionScraper.parseFatSecretHtml("火腿蛋餅", html(serving = serving)).isEmpty())
        }
    }

    @Test fun queryPreservesIngredientsAndBrandAndRejectsDifferentDishes() {
        for (query in listOf("火腿蛋餅", "紅燒牛肉麵", "麥當勞雞塊")) {
            assertEquals(query, WebNutritionScraper.buildFatSecretQuery(query))
        }
        for (title in listOf("玉米蛋餅", "原味蛋餅", "火腿蛋堡", "牛肉醬")) {
            assertTrue(title, WebNutritionScraper.parseFatSecretHtml("火腿蛋餅", html(title)).isEmpty())
        }
        val food = WebNutritionScraper.parseFatSecretHtml("火腿蛋餅", html("火腿蛋饼")).single()
        assertEquals("", food.aliases)
        assertTrue(WebNutritionScraper.parseFatSecretHtml("麥當勞雞塊", html("雞塊")).isEmpty())
    }
}
