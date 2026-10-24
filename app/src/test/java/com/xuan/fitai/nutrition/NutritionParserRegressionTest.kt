package com.xuan.fitai.nutrition

import org.junit.Assert.*
import org.junit.Test

class NutritionParserRegressionTest {
    private val citation = GoogleAiCitation("來源", "https://example.org/nutrition")
    private fun parse(text: String) = GoogleAiNutritionParser.parse("火腿蛋餅", "1份",
        GoogleAiPage(text, citations = listOf(citation)), GoogleAiNutritionSource.searchUrl("火腿蛋餅", "1份", 1))

    @Test fun totalFatMustNotUseSaturatedFat() {
        val food = parse("火腿蛋餅每1份，熱量300kcal、蛋白質15g、碳水37g、飽和脂肪3g、總脂肪10g")!!
        assertEquals(10f, food.fat)
    }
    @Test fun multiServingAnswerMustNotBecomeOneServing() {
        val food = parse("火腿蛋餅每11份，熱量3300kcal、蛋白質132g、碳水330g、脂肪110g")
        assertNull("11份 was accepted as ${food?.servingLabel}: ${food?.calories}", food)
    }
    @Test fun anotherDishMustNotSupplyTheRequestedDishNumbers() {
        val food = parse("火腿蛋餅與原味蛋餅每1份營養比較。原味蛋餅：熱量261kcal、蛋白質11g、碳水34g、脂肪9g。火腿蛋餅：熱量294kcal、蛋白質15g、碳水37g、脂肪10g")
        assertTrue("wrong dish calories=${food?.calories}", food == null || food.calories == 294f)
    }
    @Test fun supportedMealNameMustGenerateAnAllowedProviderUrl() {
        val name = "香煎雞胸肉便當（半碗白飯、花椰菜、滷蛋、炒高麗菜）加一杯大杯無糖豆漿"
        assertTrue(name.length in 2..80)
        val url = GoogleAiNutritionSource.providerUrl(WebAiProvider.GOOGLE, name, "1份", 1)
        assertEquals("generated url length=${url.length}", WebAiProvider.GOOGLE, WebAiProvider.fromUrl(url))
    }
    @Test fun partialAndChineseServingCountsAndLargerMassAreRejected() {
        for (basis in listOf("每21份", "每0.1份", "每1.1份", "每十一份", "每二十一份", "每１１份")) {
            assertNull(basis, parse("火腿蛋餅$basis，熱量300kcal、蛋白質15g、碳水37g、脂肪10g"))
        }
        for (mass in listOf("1100g", "2100公克", "0.100g", "1100克")) {
            assertNull(mass, GoogleAiNutritionParser.parse("火腿蛋餅", "100g",
                GoogleAiPage("火腿蛋餅每$mass，熱量300kcal、蛋白質15g、碳水37g、脂肪10g", citations = listOf(citation)),
                GoogleAiNutritionSource.searchUrl("火腿蛋餅", "100g", 1)))
        }
    }
    @Test fun ordinarySingleServingSpellingsRemainUsable() {
        for (basis in listOf("每1份", "每份", "一份", "每一份", "每1.0份", "每１份", "每份141g")) {
            assertEquals(basis, 294f, parse("火腿蛋餅$basis，熱量294kcal、蛋白質15g、碳水37g、脂肪10g")!!.calories, 0f)
        }
    }
    @Test fun verticalTableCannotOverrideItsExplicitServingBasisWithTheIntro() {
        val page = GoogleAiPage("查詢火腿蛋餅每1份", tables = listOf(listOf(
            listOf("營養成分", "每11份"), listOf("熱量", "3300kcal"), listOf("蛋白質", "132g"),
            listOf("碳水", "330g"), listOf("脂肪", "110g"))), citations = listOf(citation))
        assertNull(GoogleAiNutritionParser.parse("火腿蛋餅", "1份", page,
            GoogleAiNutritionSource.searchUrl("火腿蛋餅", "1份", 1)))
    }
    @Test fun fatSubtypesCannotSupplyMissingTotalAndRepeatedRecordsAreRejected() {
        val base = "火腿蛋餅每1份，熱量300kcal、蛋白質15g、碳水37g、"
        for (subtype in listOf("飽和脂肪", "反式脂肪", "單元不飽和脂肪", "多元不飽和脂肪")) {
            assertNull(subtype, parse(base + subtype + "3g"))
            assertEquals(subtype, 10f, parse(base + subtype + "3g、總脂肪10g")!!.fat, 0f)
        }
        assertNull(parse(base + "脂肪10g、蛋白質11g"))
        assertNull(parse("火腿蛋餅與原味蛋餅營養比較，每1份原味蛋餅熱量261kcal、蛋白質11g、碳水34g、脂肪9g"))
    }
    @Test fun maximumCjkMealNameFitsEveryProviderWithoutTruncatingNameOrBasis() {
        for (provider in WebAiProvider.entries) for (basis in listOf("100g", "1份", "1個", "1片", "1碗", "1包", "1杯")) {
            for (attempt in 1..3) {
                val name = "牛".repeat(80)
                val url = GoogleAiNutritionSource.providerUrl(provider, name, basis, attempt)
                assertEquals("$provider $basis length=${url.length}", provider, WebAiProvider.fromUrl(url))
                assertTrue(isNutritionSourceUrl(url))
                val query = java.net.URI(url).rawQuery.split('&').first { it.startsWith("q=") }.substring(2)
                val decoded = java.net.URLDecoder.decode(query, "UTF-8")
                assertTrue(decoded.contains(name)); assertTrue(decoded.contains("每$basis"))
            }
        }
    }

    @Test fun nutrientGramAmountsCannotDeclareOrHideAWeightBasis() {
        val url = GoogleAiNutritionSource.searchUrl("火腿蛋餅", "100g", 1)
        val per100g = GoogleAiPage("火腿蛋餅每100.0公克，熱量300大卡、蛋白質15克、碳水37克、脂肪10克", citations = listOf(citation))
        assertEquals(300f, GoogleAiNutritionParser.parse("火腿蛋餅", "100g", per100g, url)!!.calories)
        val wrongBasis = GoogleAiPage("火腿蛋餅每1100g，熱量800kcal、蛋白質100g、碳水55g、脂肪20g", citations = listOf(citation))
        assertNull(GoogleAiNutritionParser.parse("火腿蛋餅", "100g", wrongBasis, url))
        assertNull(parse("火腿蛋餅每100g與每1份（141g），熱量300kcal、蛋白質15g、碳水37g、脂肪10g"))
    }

    @Test fun nutrientWordsInsideTheDishNameDoNotHideItsServingBasis() {
        val name = "高蛋白質低熱量火腿蛋餅"
        val page = GoogleAiPage("${name}每1份，熱量294kcal、蛋白質15g、碳水37g、脂肪10g", citations = listOf(citation))
        assertEquals(294f, GoogleAiNutritionParser.parse(name, "1份", page,
            GoogleAiNutritionSource.searchUrl(name, "1份", 1))!!.calories)
    }

    @Test fun markdownPipeTableAndSinglePipeRowAreParsedCleanly() {
        val mdTable = """
            | 餐點 | 基準 | 熱量kcal | 蛋白質g | 碳水g | 脂肪g |
            |---|---|---|---|---|---|
            | 卡拉雞漢堡 | 1份 | 520 | 22 | 45 | 28 |
            類型：配方估算；假設：卡拉雞排100g、漢堡麵包65g、生菜20g、美乃滋15g。
        """.trimIndent()
        val parsedTable = GoogleAiNutritionParser.parse("卡拉雞漢堡", "1份",
            GoogleAiPage(mdTable, citations = listOf(citation)),
            GoogleAiNutritionSource.searchUrl("卡拉雞漢堡", "1份", 1))!!
        assertEquals(520f, parsedTable.calories)
        assertEquals(22f, parsedTable.protein)

        val singleRow = "| 卡拉雞漢堡 | 1份 | 520kcal | 22g | 45g | 28g |\n類型：配方估算\n假設：卡拉雞排100g、漢堡麵包65g。"
        val parsedSingle = GoogleAiNutritionParser.parse("卡拉雞漢堡", "1份",
            GoogleAiPage(singleRow, citations = listOf(citation)),
            GoogleAiNutritionSource.searchUrl("卡拉雞漢堡", "1份", 1))!!
        assertEquals(520f, parsedSingle.calories)
        assertEquals(28f, parsedSingle.fat)
    }

    @Test fun trailingCitationCardNoiseAfterDisclaimerDoesNotBreakProseParser() {
        val noisy = """
            火腿蛋餅、每份141g、294kcal、蛋白質15g、碳水37g、脂肪10g。來源：天主教聖馬爾定醫院
            如需查詢其他餐點的營養數值，請告訴我。
            AI 可能會出錯，請查證回覆內容
            聖馬爾定醫院 常用中式食物熱量及營養成份表：每100g 熱量 250大卡、蛋白質 12克
        """.trimIndent()
        val food = parse(noisy)!!
        assertEquals(294f, food.calories)
        assertEquals(15f, food.protein)
    }

}
