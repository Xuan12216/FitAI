package com.xuan.fitai.nutrition

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GoogleAiNutritionTest {
    private val citation = GoogleAiCitation("公開參考來源", "https://meals.ntu.edu.tw/CampusDining/kcal/breakfast")
    private fun page(name: String = "火腿蛋餅") = GoogleAiPage(
        "每 1 份$name（約 140~145 公克）的營養成分大約如下：\n熱量：約 265 ~ 344 大卡\n蛋白質：約 15 公克\n碳水化合物（醣類）：約 37 公克\n脂肪：約 10 公克", citations = listOf(citation))
    private fun parse(page: GoogleAiPage, basis: String = "1份") = GoogleAiNutritionParser.parse("火腿蛋餅", basis, page, GoogleAiNutritionSource.searchUrl("火腿蛋餅", basis, 1))

    @Test fun realAndroidReplyFormatPreservesServingAndUsesDeclaredRangeMidpoint() {
        val food = parse(page())!!
        assertEquals(304.5f, food.calories); assertEquals(15f, food.protein)
        assertEquals(37f, food.carbs); assertEquals(10f, food.fat)
        assertEquals("1份", food.servingLabel)
        assertTrue(food.description.contains("中值"))
        assertNull(parse(page(), "100g"))
    }

    @Test fun comparisonTableSelectsWholeDishRatherThanOtherRows() {
        val p = GoogleAiPage("一份火腿蛋餅的營養比較：", listOf(listOf(
            listOf("餐點", "重量(g)", "熱量(kcal)", "蛋白質(g)", "碳水化合物(g)", "脂肪(g)"),
            listOf("原味蛋餅", "117", "261", "11", "34", "9"),
            listOf("火腿蛋餅", "~150", "350–380", "12–15", "35–40", "15–18")
        )), listOf(citation))
        val food = parse(p)!!
        assertEquals(365f, food.calories); assertEquals(13.5f, food.protein)
        assertNull(parse(p, "100g"))
        assertNull(parse(p.copy(tables = listOf(p.tables.single().take(2)))))
    }

    @Test fun mathLayoutWithOneDigitPerLineStillParsesDeclaredNutrients() {
        val text = "火腿蛋餅每1份\n熱量：\n3\n0\n0\nk\nc\na\nl\n蛋白質：\n1\n5\ng\n碳水化合物：\n3\n7\ng\n脂肪：\n1\n0\ng"
        val food = parse(page().copy(text = text))!!
        assertEquals(300f, food.calories); assertEquals(15f, food.protein)
        assertEquals(37f, food.carbs); assertEquals(10f, food.fat)
    }

    @Test fun compactLiveReplyWithoutCalorieLabelRetainsExplicitUnitsAndBasis() {
        for (text in listOf(
            "火腿蛋餅、每份141g、294kcal、蛋白質15g、碳水37g、脂肪10g。來源：天主教聖馬爾定醫院",
            "火腿蛋餅｜每1份（141g）｜294kcal｜蛋白質15g｜碳水37g｜脂肪10g"
        )) {
            val food = parse(page().copy(text = text))!!
            assertEquals(294f, food.calories); assertEquals(15f, food.protein)
            assertEquals(37f, food.carbs); assertEquals(10f, food.fat)
            assertEquals("1份", food.servingLabel)
        }
    }

    @Test fun compactReplyStillRejectsAmbiguousEnergyMissingMacrosAndWrongBasis() {
        val text = "火腿蛋餅每1份、294kcal、蛋白質15g、碳水37g、脂肪10g"
        assertNull(parse(page().copy(text = text + "，另有350kcal")))
        assertNull(parse(page().copy(text = text.replace("294kcal", "294"))))
        assertNull(parse(page().copy(text = text.replace("、脂肪10g", ""))))
        assertNull(parse(page().copy(text = text), "100g"))
        assertNull(parse(page().copy(text = text, citations = emptyList())))
    }

    @Test fun incompleteUnsourcedWrongDishAndBlockedAnswersAreRejected() {
        assertNull(parse(page().copy(citations = emptyList())))
        assertNull(parse(page().copy(blocked = true)))
        assertNull(parse(page().copy(text = "每1份火腿蛋堡 熱量300大卡 蛋白質15克 碳水37克 脂肪10克")))
        assertNull(parse(page().copy(text = "每1份火腿蛋餅 熱量300大卡 蛋白質15克")))
        assertFalse(isGoogleCitationUrl("javascript:alert(1)"))
        assertFalse(isGoogleCitationUrl("https://user:pass@example.com/source"))
        assertFalse(isGoogleAiSearchUrl("https://other.example/search?udm=50"))
    }

    @Test fun verticalTableUsesRequestedBasisWithoutMixingPerServingAndPer100g() {
        val p = GoogleAiPage("火腿蛋餅，每100g與每1份資料如下：", listOf(listOf(
            listOf("營養成分", "每100g", "每1份"),
            listOf("熱量", "200 kcal", "300 kcal"), listOf("蛋白質", "10 g", "15 g"),
            listOf("碳水化合物", "24 g", "36 g"), listOf("脂肪", "7 g", "10.5 g")
        )), listOf(citation))
        assertEquals(200f, parse(p, "100g")!!.calories)
        assertEquals(300f, parse(p, "1份")!!.calories)
        assertNull(parse(page().copy(text = "每100g與每1份火腿蛋餅，熱量300大卡 蛋白質15克 碳水37克 脂肪10克")))
    }

    @Test fun atMostThreeSearchesPerAssessmentWithoutCooldownBetweenManualRequests() = runTest {
        var calls = 0
        val source = GoogleAiNutritionSource({ calls++; GoogleAiPage(text = "無法解析") }, clock = { 100_000 })
        val failed = source.lookup("火腿蛋餅", "1份")
        assertEquals(3, calls); assertEquals(3, failed.attempts); assertNull(failed.assessment)
        assertEquals(3, source.lookup("牛肉麵", "1份").attempts)
        assertEquals(6, calls)
    }

    @Test fun blockedOrUnavailablePageStopsThisAssessmentWithoutPersistentCooldown() = runTest {
        var calls = 0
        val source = GoogleAiNutritionSource({ calls++; GoogleAiPage(blocked = true) }, clock = { 100_000 })
        assertEquals(1, source.lookup("火腿蛋餅", "1份").attempts)
        assertEquals(1, source.lookup("火腿蛋餅", "1份").attempts)
        assertEquals(2, calls)
        val unavailable = GoogleAiNutritionSource({ GoogleAiPage(failure = "逾時") }, clock = { 100_000 })
        assertEquals(1, unavailable.lookup("火腿蛋餅", "1份").attempts)
    }

    @Test fun manualRetryOnlySendsOneQueryAndRecordsFailuresAndHttpStatus() = runTest {
        var calls = 0
        val source = GoogleAiNutritionSource({ calls++; GoogleAiPage(text = "無法解析") })
        assertTrue(source.retry("火腿蛋餅", "1份").canRetry)
        assertEquals(1, calls)
        assertEquals(GoogleAiTrafficMonitor.Outcome.INCOMPLETE, source.monitor.snapshot().lastOutcome)
        val blocked = GoogleAiNutritionSource({ GoogleAiPage(blocked = true, httpStatus = 429) })
        assertFalse(blocked.lookup("火腿蛋餅", "1份").canRetry)
        assertEquals(429, blocked.monitor.snapshot().lastHttpStatus)
        assertEquals(GoogleAiTrafficMonitor.Outcome.BLOCKED, blocked.monitor.snapshot().lastOutcome)
    }

    @Test fun denseTrafficStopsAdditionalAutomaticRetriesButAllowsExplicitSingleRetry() = runTest {
        var now = 100_000L
        val monitor = GoogleAiTrafficMonitor { now }
        val source = GoogleAiNutritionSource({ GoogleAiPage(text = "無法解析") }, monitor = monitor)
        assertEquals(3, source.lookup("火腿蛋餅", "1份").attempts)
        assertEquals(3, source.lookup("牛肉麵", "1份").attempts)
        assertEquals(1, source.lookup("雞肉飯", "1份").attempts)
        assertTrue(monitor.snapshot().dense)
        assertEquals(1, source.retry("雞肉飯", "1份").attempts)
        now += 60_000
        assertFalse(monitor.snapshot().dense)
        assertEquals(3, source.lookup("雞肉飯", "1份").attempts)
    }

    @Test fun automaticRotationTriesEachProviderOnceAndManualRetryExcludesLastProvider() = runTest {
        val selected = mutableListOf<WebAiProvider>()
        val source = GoogleAiNutritionSource({ url ->
            selected += WebAiProvider.fromUrl(url)!!
            GoogleAiPage(text = "無法解析")
        }, rotation = WebAiRotation())
        assertEquals(3, source.lookup("火腿蛋餅", "1份").attempts)
        assertEquals(WebAiProvider.entries.toSet(), selected.toSet())
        val last = selected.last()
        assertEquals(1, source.retry("火腿蛋餅", "1份").attempts)
        assertNotEquals(last, selected.last())
        assertEquals(4, selected.size)
    }

    @Test fun nonBlockingNetworkFailureCanFallBackToAnotherProviderButCaptchaStopsImmediately() = runTest {
        var calls = 0
        val source = GoogleAiNutritionSource({ calls++; GoogleAiPage(failure = "網頁連線失敗") }, rotation = WebAiRotation())
        assertEquals(3, source.lookup("火腿蛋餅", "1份").attempts)
        assertEquals(3, calls)
        calls = 0
        val blocked = GoogleAiNutritionSource({ calls++; GoogleAiPage(blocked = true) }, rotation = WebAiRotation())
        assertEquals(1, blocked.lookup("火腿蛋餅", "1份").attempts)
        assertEquals(1, calls)
    }

    @Test fun completeFactsCanFinishWhileUnrelatedAnswerTextKeepsGrowing() {
        val tracker = GoogleAiReadinessTracker()
        assertFalse(tracker.observe(page(), "facts"))
        assertFalse(tracker.observe(page().copy(text = page().text + "來源補充"), "facts"))
        assertTrue(tracker.observe(page().copy(text = page().text + "來源補充和其他建議"), "facts"))
    }

    @Test fun shortCompleteAnswerIsAcceptedButChangingNumbersMustSettle() {
        val tracker = GoogleAiReadinessTracker()
        val short = page().copy(text = "短答")
        assertFalse(tracker.observe(short, "fat=1"))
        assertFalse(tracker.observe(short, "fat=15"))
        assertFalse(tracker.observe(short, "fat=15"))
        assertTrue(tracker.observe(short, "fat=15"))
        assertFalse(GoogleAiReadinessTracker().observe(short.copy(citations = emptyList()), "facts"))
    }

    @Test fun concurrentRequestIsSuppressedRatherThanQueuedAndNextRequestUsesCache() = runTest {
        var calls = 0
        val source = GoogleAiNutritionSource({ calls++; delay(100); page() }, clock = { 100_000 })
        val a = async { source.lookup("火腿蛋餅", "1份") }
        val b = async { source.lookup("火腿蛋餅", "2份") }
        val fresh = a.await().assessment!!
        assertNull(b.await().assessment)
        val cached = source.lookup("火腿蛋餅", "2份").assessment!!
        assertEquals(1, calls)
        assertEquals(1, source.monitor.snapshot().suppressed)
        assertFalse(fresh.reference.fromCache); assertTrue(cached.reference.fromCache)
        assertEquals(fresh.reference.fetchedAt, cached.reference.fetchedAt)
        assertEquals(304.5f, fresh.analysis.calories)
    }

    @Test fun unsupportedUnitsNeverSearchAndCancellationDoesNotRetry() = runTest {
        var calls = 0
        val source = GoogleAiNutritionSource({ calls++; throw CancellationException() }, clock = { 100_000 })
        assertEquals(0, source.lookup("火腿蛋餅", "大份").attempts)
        assertTrue(runCatching { source.lookup("火腿蛋餅", "1份") }.exceptionOrNull() is CancellationException)
        assertEquals(1, calls)
    }
    @Test fun duckPreparationDeadlineDoesNotShortenAnswerDeadline() {
        val preparing = GoogleAiPage(preparing = true, preparationStage = "setSpeed")
        assertFalse(GoogleAiWaitPolicy.preparationTimedOut(preparing, 7_999))
        assertTrue(GoogleAiWaitPolicy.preparationTimedOut(preparing, 8_000))
        assertFalse(GoogleAiWaitPolicy.preparationTimedOut(preparing.copy(preparing = false, preparationStage = "sent"), 24_999))
    }

    @Test fun failedManualLookupDoesNotClaimItRanTheLocalFallback() = runTest {
        val source = GoogleAiNutritionSource({ GoogleAiPage(failure = "查詢準備逾時。") })
        val result = source.retry("豬排烏龍麵", "1份")
        assertEquals(1, result.attempts)
        assertTrue(result.message.contains("原有數值已保留"))
        assertFalse(result.message.contains("改用台灣資料"))
    }

}
