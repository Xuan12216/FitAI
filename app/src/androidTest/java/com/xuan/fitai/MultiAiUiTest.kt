package com.xuan.fitai

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.xuan.fitai.ai.*
import com.xuan.fitai.data.model.Meal
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.nutrition.*
import com.xuan.fitai.ui.components.GoogleAiTrafficStatus
import com.xuan.fitai.ui.components.NutritionReferenceBadge
import com.xuan.fitai.ui.dashboard.DashboardViewModel
import com.xuan.fitai.ui.dashboard.ManualMealDialog
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MultiAiUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun monitoringExpansionDoesNotIssueNetworkRequests() {
        val app = ApplicationProvider.getApplicationContext<FitAIApplication>()
        val before = app.googleAiTrafficMonitor.snapshot().requests
        compose.setContent { MaterialTheme { GoogleAiTrafficStatus() } }
        compose.onNodeWithText("查詢監測").performClick()
        compose.onNodeWithText("本次開啟 App：", substring = true).assertExists()
        compose.onNodeWithText("只記錄本機次數及狀態", substring = true).assertExists()
        compose.onNodeWithText("收起查詢監測").performClick()
        assertEquals(before, app.googleAiTrafficMonitor.snapshot().requests)
    }

    @Test fun badgeNamesActualDuckModelAndRetainsSourceDetails() {
        val food = NutritionFood("DUCK-AI-1", "火腿蛋餅", "", "估算", 300f, 15f, 37f, 10f,
            "Duck.ai / GPT-6 Luna", "https://duck.ai/", "1份")
        val json = NutritionReferenceJson.encode(NutritionReference(1000L, false, listOf(food), food.id,
            "測試來源回答", listOf(GoogleAiCitation("臺大", NTU_BREAKFAST_URL))))
        compose.setContent { MaterialTheme { NutritionReferenceBadge(json) } }
        compose.onNodeWithText("Duck.ai / GPT-6 Luna 估算").performClick()
        compose.onNodeWithText("Duck.ai / GPT-6 Luna 回答節錄（取得時）").assertExists()
        compose.onNodeWithText("測試來源回答").assertExists()
    }

    @Test fun recipeEstimateBadgeAndAssumptionsSurviveJsonAndSelection() {
        val assumption = "豬排120g、熟烏龍麵200g"
        val food = NutritionFood("GOOGLE-AI-1", "豬排烏龍麵", "", "配方估算", 800f, 35f, 100f, 25f,
            "Google AI", GoogleAiNutritionSource.searchUrl("豬排烏龍麵", "1份", 1), "1份")
        val reference = NutritionReference(1000L, false, listOf(food), food.id,
            "類型：配方估算", listOf(GoogleAiCitation("參考食材", NTU_BREAKFAST_URL)), true, assumption)
        val json = NutritionReferenceJson.withSelection(NutritionReferenceJson.encode(reference), food.id)!!
        assertEquals(reference, NutritionReferenceJson.decode(json))
        compose.setContent { MaterialTheme { NutritionReferenceBadge(json) } }
        compose.onNodeWithText("Google AI 配方估算").performClick()
        compose.onNodeWithText("配方估算（依一般配方，非完整營養標示）").assertExists()
        compose.onNodeWithText("食材與份量假設：$assumption").assertExists()
    }

    @Test fun failedSingleRetryKeepsPreviousMealValuesAndGemmaAttribution() {
        val app = ApplicationProvider.getApplicationContext<FitAIApplication>()
        val model = Model()
        var automatic = 0
        var manual = 0
        val lookup = object : GoogleAiLookupSource {
            override suspend fun lookup(name: String, portion: String): GoogleAiLookup {
                automatic++
                return GoogleAiLookup(message = "網路資料不完整", attempts = 3, canRetry = true)
            }
            override suspend fun retry(name: String, portion: String): GoogleAiLookup {
                manual++
                return GoogleAiLookup(message = "單次仍無可用資料，原有數值已保留", attempts = 1, canRetry = true)
            }
        }
        val assessment = FoodAssessment(model, lookup) { NutritionLookup(message = "使用本地估算") }
        val vm = DashboardViewModel(app.userRepository, app.mealRepository, model, app.reminderRepository,
            app.database.progressDao(), assessment)
        compose.setContent { MaterialTheme {
            ManualMealDialog(vm, initial = Meal(name = "火腿蛋餅", calories = 99f, protein = 0f, carbs = 0f, fat = 0f), onDismiss = {})
        } }
        compose.onNodeWithText("AI 估算熱量與營養素").performScrollTo().performClick()
        compose.waitUntil(5_000) { automatic == 1 && model.calls == 1 }
        compose.onNodeWithText("估算 AI：本地 Gemma").assertExists()
        compose.onNodeWithText("再查一次網路 AI").performScrollTo().performClick()
        compose.waitUntil(5_000) { manual == 1 }
        compose.onNodeWithText("單次仍無可用資料", substring = true).assertExists()
        compose.onAllNodes(hasSetTextAction() and hasText("300")).assertCountEquals(1)
        assertEquals(1, automatic); assertEquals(1, manual); assertEquals(1, model.calls)
    }

    private class Model : GemmaLocalHelper {
        var calls = 0
        override val contextTokenLimit = flowOf(4096)
        override val loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Loaded)
        override val loadedModelName = MutableStateFlow<String?>("test")
        override val visionReady = MutableStateFlow(false)
        override val audioReady = MutableStateFlow(false)
        override fun analyzeFoodFlow(foodName: String, portion: String, goal: String, referenceData: String): Flow<String> {
            calls++
            return flowOf("""{"calories":300,"protein":15,"carbs":37,"fat":10,"suitable":true,"advice":"測試","reasoning":"一份總量"}""")
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
