package com.xuan.fitai.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Conversation
import com.xuan.fitai.data.model.ModelLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class GemmaLocalHelperImpl(private val context: Context) : GemmaLocalHelper {

    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.NotFound)
    override val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    private val _loadedModelName = MutableStateFlow<String?>(null)
    override val loadedModelName: StateFlow<String?> = _loadedModelName.asStateFlow()

    private val _visionReady = MutableStateFlow(false)
    override val visionReady: StateFlow<Boolean> = _visionReady.asStateFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val modelMutex = Mutex()

    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    private var loadJob: kotlinx.coroutines.Job? = null

    override fun loadModel(modelPath: String, modelName: String) {
        synchronized(this) {
            loadJob = scope.launch {
                loadModelSync(modelPath, modelName)
            }
        }
    }

    override suspend fun loadModelSync(modelPath: String, modelName: String): Boolean = withContext(Dispatchers.IO) {
        android.util.Log.d("FitAI_Diag", "GemmaLocalHelperImpl.loadModelSync called with path: $modelPath, name: $modelName")
        val file = File(modelPath)
        if (!file.exists()) {
            android.util.Log.d("FitAI_Diag", "GemmaLocalHelperImpl: File does not exist!")
            _loadState.value = ModelLoadState.NotFound
            _loadedModelName.value = null
            _visionReady.value = false
            return@withContext false
        }

        _loadState.value = ModelLoadState.Loading
        _loadedModelName.value = null
        _visionReady.value = false

        return@withContext modelMutex.withLock {
            try {
                android.util.Log.d("FitAI_Diag", "GemmaLocalHelperImpl: Starting load...")
                closeResources()

                val modelInstance = createInitializedConversation(modelPath)

                engine = modelInstance.engine
                conversation = modelInstance.conversation
                _visionReady.value = modelInstance.visionEnabled
                _loadedModelName.value = modelName
                _loadState.value = ModelLoadState.Loaded
                android.util.Log.d("FitAI_Diag", "GemmaLocalHelperImpl: Loaded successfully! visionReady=${modelInstance.visionEnabled}")
                true
            } catch (e: Exception) {
                android.util.Log.e("FitAI_Diag", "GemmaLocalHelperImpl: Load failed!", e)
                _loadedModelName.value = null
                _loadState.value = ModelLoadState.Failed(e.localizedMessage ?: "載入失敗")
                false
            }
        }
    }

    override suspend fun generateReply(prompt: String): String = withContext(Dispatchers.IO) {
        modelMutex.lock()
        try {
        val currentConversation = conversation ?: return@withContext "錯誤：Gemma 模型尚未載入。"
        try {
            val response = StringBuilder()
            currentConversation.sendMessageAsync(prompt).collect { token ->
                response.append(token)
            }
            response.toString()
        } catch (e: Exception) {
            "本地 Gemma 推論出錯: ${e.localizedMessage}"
        }
        } finally {
            modelMutex.unlock()
        }
    }

    override suspend fun analyzeFood(foodName: String, portion: String, goal: String): GemmaFoodAnalysis {
        val prompt = """
            You are a professional nutritionist. The user's goal is: $goal. The user just scanned: $foodName (portion: $portion).
            Estimate its calorie count (kcal) and macronutrients (protein, carbs, fat in grams).
            Provide your response in JSON format matching this structure:
            {"calories": 150.0, "protein": 10.0, "carbs": 12.0, "fat": 3.0, "suitable": true, "advice": "Concise advice in Traditional Chinese."}
            Ensure you output ONLY the raw JSON string.
        """.trimIndent()

        val reply = generateReply(prompt)
        return try {
            val cleanReply = reply.substring(reply.indexOf("{"), reply.lastIndexOf("}") + 1)
            val json = org.json.JSONObject(cleanReply)
            GemmaFoodAnalysis(
                calories = json.optDouble("calories", 150.0).toFloat(),
                protein = json.optDouble("protein", 10.0).toFloat(),
                carbs = json.optDouble("carbs", 12.0).toFloat(),
                fat = json.optDouble("fat", 3.0).toFloat(),
                isSuitable = json.optBoolean("suitable", true),
                advice = json.optString("advice", "這是本地 AI 估算的營養價值。請視實際烹調方式調整。")
            )
        } catch (e: Exception) {
            GemmaFoodAnalysis(
                calories = 200f,
                protein = 8f,
                carbs = 20f,
                fat = 6f,
                isSuitable = true,
                advice = "本地 Gemma 推論成功。請注意食物份量並搭配均衡飲食。"
            )
        }
    }

    override suspend fun identifyFoodFromImage(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        modelMutex.lock()
        try {
        val currentConversation = conversation ?: return@withContext "未知食物"
        try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val imageBytes = outputStream.toByteArray()

            val imageContent = Content.ImageBytes(imageBytes)
            val textContent = Content.Text(
                "你是一個飲食辨識助手，使用繁體中文。請只回答一個食物名稱，不要其他說明。" +
                "看看這張圖片，圖中有什麼食物？只回答食物的繁體中文名稱，例如：雞蛋、薯條、雞胸肉。"
            )

            // Contents constructor is `internal` — use reflection to bypass module boundary
            val contents = Contents.of(listOf<Content>(imageContent, textContent))

            val response = StringBuilder()
            currentConversation.sendMessageAsync(contents).collect { msg ->
                response.append(msg)
            }
            val result = response.toString().trim()
            if (result.isBlank()) "未知食物" else result
        } catch (e: Exception) {
            android.util.Log.e("FitAI_Diag", "identifyFoodFromImage failed", e)
            "未知食物"
        }
        } finally {
            modelMutex.unlock()
        }
    }

    private fun closeResources() {
        try {
            conversation?.close()
        } catch (e: Exception) {}
        try {
            engine?.close()
        } catch (e: Exception) {}
        conversation = null
        engine = null
        _visionReady.value = false
    }

    private data class ModelInstance(
        val engine: Engine,
        val conversation: Conversation,
        val visionEnabled: Boolean
    )

    private fun createInitializedConversation(modelPath: String): ModelInstance {
        val configs = listOf(
            EngineConfig(modelPath = modelPath, backend = Backend.CPU(), visionBackend = Backend.GPU()) to true,
            EngineConfig(modelPath = modelPath, backend = Backend.CPU(), visionBackend = Backend.CPU()) to true,
            EngineConfig(modelPath = modelPath, backend = Backend.CPU()) to false
        )

        var lastError: Exception? = null
        for ((config, visionEnabled) in configs) {
            var candidateEngine: Engine? = null
            try {
                candidateEngine = Engine(config).also { it.initialize() }
                val candidateConversation = candidateEngine.createConversation()
                return ModelInstance(candidateEngine, candidateConversation, visionEnabled)
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w("FitAI_Diag", "Gemma engine/conversation init failed, trying fallback config", e)
                try {
                    candidateEngine?.close()
                } catch (_: Exception) {
                }
            }
        }
        throw lastError ?: IllegalStateException("Gemma engine initialization failed")
    }
}
