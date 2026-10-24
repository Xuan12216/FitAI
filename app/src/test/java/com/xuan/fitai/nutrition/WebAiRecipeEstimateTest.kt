package com.xuan.fitai.nutrition

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WebAiRecipeEstimateTest {
    private val name = "豬排烏龍麵"
    private val assumption = "炸豬排120g、熟烏龍麵200g、湯250g，合計每1份"
    private val page = GoogleAiPage(
        "$name 每1份，熱量800kcal、蛋白質35g、碳水100g、脂肪25g\n類型：配方估算\n假設：$assumption",
        citations = listOf(GoogleAiCitation("參考食材", "https://example.org/food")))
    private fun parse(p: GoogleAiPage) = GoogleAiNutritionParser.parse(name, "1份", p,
        GoogleAiNutritionSource.searchUrl(name, "1份", 1))

    @Test fun recipeEstimatePreservesNumbersAssumptionsAndActualProvider() = runTest {
        val result = GoogleAiNutritionSource({ page }).retry(name, "1份").assessment!!
        assertEquals(800f, result.analysis.calories)
        assertTrue(result.reference.recipeEstimate)
        assertEquals(assumption, result.reference.servingAssumption)
        assertEquals(WebAiProvider.GOOGLE, result.reference.foods.single().webAiProvider)
        assertTrue(result.analysis.advice.contains("配方估算"))
        assertTrue(result.reference.foods.single().description.contains(assumption))
    }
    @Test fun recipeWithoutQuantifiedAssumptionsOrSourcesIsRejected() {
        assertNull(parse(page.copy(text = page.text.substringBefore("假設"))))
        assertNull(parse(page.copy(text = page.text.replace(assumption, "一般大小的一份"))))
        assertNull(parse(page.copy(citations = emptyList())))
    }
    @Test fun publishedAndLegacyAnswersAreNotMarkedAsRecipeEstimates() = runTest {
        for (suffix in listOf("\n類型：資料", "")) {
            val reply = page.copy(text = page.text.substringBefore("\n類型") + suffix)
            val result = GoogleAiNutritionSource({ reply }).retry(name, "1份").assessment!!
            assertFalse(result.reference.recipeEstimate)
            assertNull(result.reference.servingAssumption)
        }
    }
    @Test fun portionScalingRetainsRecipeDisclosureAndBasisAssumptions() = runTest {
        val result = GoogleAiNutritionSource({ page }).retry(name, "2份").assessment!!
        val scaled = NutritionPortionCalculator.apply(result.analysis, result.reference, "2份")
        assertEquals(1600f, scaled.calories)
        assertEquals(70f, scaled.protein)
        assertTrue(scaled.reasoning.contains("配方估算"))
        assertTrue(scaled.reasoning.contains(assumption))
    }
    @Test fun recipeMarkerCannotTurnMissingNutrientsIntoZero() {
        assertNull(parse(page.copy(text = page.text.replace("蛋白質35g", "蛋白質—g"))))
        assertNull(parse(page.copy(text = page.text.replace("脂肪25g", "脂肪—g"))))
    }
    @Test fun realCsvReplyIsAcceptedOnlyWithTheRequestedServingUnit() {
        val csv = "餐點,基準,熱量kcal,蛋白質g,碳水g,脂肪g\n豬排烏龍麵,1碗 (約 670g),616.5,32.1,73.4,19.5\n類型:配方估算; 假設:熟烏龍麵250g、炸豬排120g、高湯與配菜300g。"
        assertNull(parse(page.copy(text = csv)))
        val valid = parse(page.copy(text = csv.replace("1碗", "1份")))!!
        assertEquals(616.5f, valid.calories); assertEquals(32.1f, valid.protein)
        assertEquals(73.4f, valid.carbs); assertEquals(19.5f, valid.fat)
        assertEquals("1份", valid.servingLabel)
    }
    @Test fun csvCannotMixDishesOrUseSaturatedFatAsTotalFat() {
        val csv = "餐點,基準,熱量kcal,蛋白質g,碳水g,脂肪g\n豬排烏龍麵,1份,800,35,100,25\n類型:配方估算; 假設:豬排120g、熟烏龍麵200g。"
        assertNull(parse(page.copy(text = "查詢豬排烏龍麵\n" + csv.replace("豬排烏龍麵,1份", "雞排烏龍麵,1份"))))
        assertNull(parse(page.copy(text = csv.replace("脂肪g", "飽和脂肪g"))))
        assertNull(parse(page.copy(text = csv.replace("1份", "11份"))))
    }

}
