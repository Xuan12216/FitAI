package com.xuan.fitai.nutrition

import android.graphics.Bitmap
import com.xuan.fitai.ai.*
import com.xuan.fitai.data.model.ModelLoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FoodAssessmentTest {
    private val reference = NutritionReference(1000, false, listOf(NutritionFood("1", "白飯", "", "熟", 183f, 3.1f, 41f, .3f)))

    @Test fun retrievedFactsActuallyReachLocalModel() = runTest {
        val model = Model()
        val events = FoodAssessment(model) { NutritionLookup(reference, "已提供") }.analyze("白飯", "200g", "維持", true).toList()
        assertTrue(model.reference.contains("183.0 kcal"))
        assertTrue(model.reference.contains("per 100g"))
        assertEquals(reference, events.filterIsInstance<FoodAssessmentEvent.Lookup>().single().result.reference)
        assertEquals("local-result", events.filterIsInstance<FoodAssessmentEvent.Token>().single().text)
    }
    @Test fun disabledNeverAccessesNetworkOrRetainsOldReference() = runTest {
        val model = Model()
        val events = FoodAssessment(model) { error("must not query") }.analyze("白飯", "1份", "維持", false).toList()
        assertEquals("", model.reference)
        assertNull(events.filterIsInstance<FoodAssessmentEvent.Lookup>().single().result.reference)
    }
    @Test fun timeoutFallsBackAndNeverAddsSourceBadge() = runTest {
        val model = Model()
        val events = FoodAssessment(model) { delay(21_000); NutritionLookup(reference, "too late") }
            .analyze("白飯", "1份", "維持", true).toList()
        assertEquals("", model.reference)
        val result = events.filterIsInstance<FoodAssessmentEvent.Lookup>().single().result
        assertNull(result.reference); assertTrue(result.message.contains("逾時"))
    }
    @Test fun networkErrorFallsBackButUserCancellationPropagates() = runTest {
        val model = Model()
        assertTrue(FoodAssessment(model) { throw java.io.IOException() }.analyze("白飯", "1份", "維持", true).toList().any { it is FoodAssessmentEvent.Token })
        var cancelled = false
        try { FoodAssessment(model) { throw CancellationException("user cancelled") }.analyze("白飯", "1份", "維持", true).toList() }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }
    @Test fun smallContextSkipsRetrievalAndUnreadyModelNeverStartsIt() = runTest {
        val model = Model(512)
        val events = FoodAssessment(model) { error("must not query") }.analyze("白飯", "1份", "維持", true).toList()
        assertNull(events.filterIsInstance<FoodAssessmentEvent.Lookup>().single().result.reference)
        model.loadState.value = ModelLoadState.Loading
        assertTrue(runCatching { FoodAssessment(model) { error("must not query") }.analyze("白飯", "1份", "維持", true).toList() }.isFailure)
    }

    @Test fun googleSuccessWorksWithoutLoadedGemmaAndNeverReestimatesLocally() = runTest {
        val model = Model(512)
        model.loadState.value = ModelLoadState.Loading
        val food = NutritionFood("GOOGLE-AI-1", "白飯", "", "Google AI 估算", 183f, 3.1f, 41f, .3f)
        val google = GoogleAiAssessment(GemmaFoodAnalysis(183f, 3.1f, 41f, .3f, true, "估算", referenceFoodId = food.id),
            NutritionReference(1000, false, listOf(food), food.id))
        val events = FoodAssessment(model, GoogleAiLookupSource { _, _ -> GoogleAiLookup(google, "Google AI", 1) }) {
            error("must not query Taiwan or local model")
        }.analyze("白飯", "200g", "維持", true).toList()
        assertEquals("", model.reference)
        assertEquals(google.reference, events.filterIsInstance<FoodAssessmentEvent.Lookup>().single().result.reference)
        assertTrue(events.filterIsInstance<FoodAssessmentEvent.Token>().single().text.contains("GOOGLE-AI-1"))
    }

    @Test fun failedGoogleFallsBackToTaiwanAndDisabledModeNeverCallsGoogle() = runTest {
        val model = Model()
        val events = FoodAssessment(model, GoogleAiLookupSource { _, _ -> GoogleAiLookup(message = "Google 失敗", attempts = 2) }) {
            NutritionLookup(reference, "台灣來源")
        }.analyze("白飯", "200g", "維持", true).toList()
        assertTrue(model.reference.contains("183.0 kcal"))
        assertTrue(events.filterIsInstance<FoodAssessmentEvent.Lookup>().single().result.message.contains("Google 失敗"))
        FoodAssessment(model, GoogleAiLookupSource { _, _ -> error("must not query Google") }) { error("must not query Taiwan") }
            .analyze("白飯", "1份", "維持", false).toList()
        assertEquals("", model.reference)
    }

    @Test fun explicitRetryInvokesRetryRoundAndNeverReplacesExistingResultWithLocalEstimation() = runTest {
        val model = Model()
        var retries = 0
        val google = object : GoogleAiLookupSource {
            override suspend fun lookup(name: String, portion: String): GoogleAiLookup = error("must not start automatic lookup")
            override suspend fun retry(name: String, portion: String): GoogleAiLookup {
                retries++
                return GoogleAiLookup(message = "資料不完整", attempts = 1, canRetry = true)
            }
        }
        val events = mutableListOf<FoodAssessmentEvent>()
        assertTrue(runCatching {
            FoodAssessment(model, google) { error("must not replace with local estimate") }
                .analyze("白飯", "1份", "維持", true, googleRetry = true).collect { events += it }
        }.isFailure)
        assertEquals(1, retries)
        assertEquals("", model.reference)
        assertTrue(events.filterIsInstance<FoodAssessmentEvent.GoogleFinished>().single().canRetry)
        assertTrue(events.none { it is FoodAssessmentEvent.Token })
    }

    @Test fun retryRoundTimeoutKeepsOldValuesAndDoesNotClaimToRunLocalFallback() = runTest {
        val model = Model()
        val google = GoogleAiLookupSource { _, _ ->
            delay(GoogleAiWaitPolicy.MANUAL_TIMEOUT_MS + 1)
            error("must time out")
        }
        val events = mutableListOf<FoodAssessmentEvent>()
        val error = runCatching {
            FoodAssessment(model, google) { error("must not run fallback on explicit retry") }
                .analyze("白飯", "1份", "維持", true, googleRetry = true).collect { events += it }
        }.exceptionOrNull()!!
        assertTrue(error.message!!.contains("原有數值已保留"))
        assertFalse(error.message!!.contains("改查台灣資料"))
        assertTrue(events.none { it is FoodAssessmentEvent.Token || it is FoodAssessmentEvent.Lookup })
        assertEquals("", model.reference)
    }

    private class Model(limit: Int = 4096) : GemmaLocalHelper {
        var reference = ""
        override val contextTokenLimit = flowOf(limit)
        override val loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Loaded)
        override val loadedModelName = MutableStateFlow<String?>("test")
        override val visionReady = MutableStateFlow(false)
        override val audioReady = MutableStateFlow(false)
        override fun analyzeFoodFlow(foodName: String, portion: String, goal: String, referenceData: String): Flow<String> {
            reference = referenceData; return flowOf("local-result")
        }
        override fun loadModel(modelPath: String, modelName: String) = error("unused")
        override suspend fun loadModelSync(modelPath: String, modelName: String): Boolean = error("unused")
        override suspend fun generateReply(prompt: String): String = error("unused")
        override fun generateReplyFlow(prompt: String): Flow<String> = error("unused")
        override suspend fun analyzeFood(foodName: String, portion: String, goal: String): GemmaFoodAnalysis = error("unused")
        override suspend fun identifyFoodFromImage(bitmap: Bitmap): String = error("unused")
        override fun identifyFoodFromImageFlow(bitmap: Bitmap): Flow<String> = error("unused")
    }
}
