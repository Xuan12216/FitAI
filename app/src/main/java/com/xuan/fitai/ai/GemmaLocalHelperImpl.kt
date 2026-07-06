package com.xuan.fitai.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ConversationConfig
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.data.datastore.UserPreferenceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class GemmaLocalHelperImpl(private val context: Context) : GemmaLocalHelper {

    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.NotFound)
    override val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    private val _loadedModelName = MutableStateFlow<String?>(null)
    override val loadedModelName: StateFlow<String?> = _loadedModelName.asStateFlow()

    private val _visionReady = MutableStateFlow(false)
    override val visionReady: StateFlow<Boolean> = _visionReady.asStateFlow()

    private var engine: Engine? = null
    private var conversationConfig: ConversationConfig? = null

    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    private var loadJob: kotlinx.coroutines.Job? = null
    private val modelMutex = Mutex()
    private val inferenceLock = Any()
    private var activeInferenceJob: Job? = null
    private var activeInferenceId: Long? = null
    private var activeInferenceType: String? = null
    private var activeInferenceStartedAt: Long = 0L
    private val inferenceIdGenerator = AtomicLong(0L)

    private val userPreferenceStore = UserPreferenceStore(context)

    private data class InferenceRequest(
        val id: Long,
        val type: String,
        val job: Job,
        val startedAt: Long
    )

    override fun loadModel(modelPath: String, modelName: String) {
        synchronized(this) {
            loadJob?.cancel()
            loadJob = scope.launch {
                loadModelSync(modelPath, modelName)
            }
        }
    }

    override suspend fun loadModelSync(modelPath: String, modelName: String): Boolean = withContext(Dispatchers.IO) {
        android.util.Log.d("FitAI_Diag", "GemmaLocalHelperImpl.loadModelSync called with path: $modelPath, name: $modelName")
        cancelActiveInference("Gemma model loading")
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
        val inference = beginInference("generateReply/${prompt.inferGemmaTaskType()}", prompt)
        var locked = false
        try {
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} waiting for modelMutex")
            modelMutex.lock()
            locked = true
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} mutex acquired")
            val currentConversation = createConversationForInference(inference)
                ?: return@withContext "錯誤：Gemma 模型尚未載入。"
            try {
                val response = StringBuilder()
                currentConversation.sendMessageAsync(prompt).collect { token ->
                    response.append(token)
                }
                android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} completed responseChars=${response.length}")
                response.toString()
            } catch (e: CancellationException) {
                android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} cancelled inside generateReply: ${e.message}")
                resetConversationAfterCancelledInference(inference)
                throw e
            } catch (e: Exception) {
                android.util.Log.e("FitAI_GemmaReq", "GemmaInference#${inference.id} failed inside generateReply", e)
                resetReusableConversationIfRecoverableSessionError(inference, e)
                "本地 Gemma 推論出錯: ${e.localizedMessage}"
            }
        } finally {
            if (locked) {
                modelMutex.unlock()
                android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} mutex released")
            }
            finishInference(inference)
        }
    }

    override fun generateReplyFlow(prompt: String): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        val taskType = prompt.inferGemmaTaskType()
        val inference = beginInference("generateReplyFlow/$taskType", prompt)
        var locked = false
        var tokenCount = 0
        var charCount = 0
        try {
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} waiting for modelMutex")
            modelMutex.lock()
            locked = true
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} mutex acquired")
            val currentConversation = createConversationForInference(inference)
                ?: throw IllegalStateException("錯誤：Gemma 模型尚未載入。")
            currentConversation.sendMessageAsync(prompt).collect { token ->
                val tokenText = token.toString()
                tokenCount += 1
                charCount += tokenText.length
                emit(tokenText)
            }
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} completed tokens=$tokenCount chars=$charCount")
        } catch (e: CancellationException) {
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} cancelled inside generateReplyFlow after tokens=$tokenCount chars=$charCount: ${e.message}")
            resetConversationAfterCancelledInference(inference)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("FitAI_GemmaReq", "GemmaInference#${inference.id} failed inside generateReplyFlow after tokens=$tokenCount chars=$charCount", e)
            resetReusableConversationIfRecoverableSessionError(inference, e)
            throw e
        } finally {
            if (locked) {
                modelMutex.unlock()
                android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} mutex released")
            }
            finishInference(inference)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun analyzeFood(foodName: String, portion: String, goal: String): GemmaFoodAnalysis {
        val prompt = """
            You are a professional nutritionist. The user's goal is: $goal. The user just scanned: $foodName (portion: $portion).
            Estimate its calorie count (kcal) and macronutrients (protein, carbs, fat in grams) using common Taiwan restaurant/brand nutrition references when recognizable.
            First infer the exact food items, quantity, and serving size from the name. For composite meals, estimate each item separately and sum them.
            Keep calories consistent with macros: calories should be close to protein*4 + carbs*4 + fat*9.
            Do not confuse a single item with a combo meal. Do not invent extremely high calories unless the portion clearly indicates multiple servings.
            Before finalizing, self-check: if calories differ from protein*4 + carbs*4 + fat*9 by more than 20%, revise the macros or calories so they are consistent.
            If unsure about portion size, state the assumed portion briefly in reasoning and use a conservative common serving estimate.
            Provide your response in JSON format matching this structure:
            {"calories": 150.0, "protein": 10.0, "carbs": 12.0, "fat": 3.0, "suitable": true, "advice": "Concise advice in Traditional Chinese.", "reasoning": "Short estimate basis in Traditional Chinese."}
            Output ONLY one raw JSON object. Do not use markdown fences. Keep advice and reasoning each under 40 Traditional Chinese characters.
        """.trimIndent()

        val reply = generateReply(prompt)
        android.util.Log.d("FitAI_Diag", "analyzeFood raw reply (${reply.length} chars): ${reply.take(500)}")
        val thinkingText = GemmaOutputParser.extractThinking(reply)
        android.util.Log.d("FitAI_Diag", "analyzeFood thinking: ${thinkingText?.take(200)}")
        return try {
            val cleanReply = GemmaOutputParser.extractJson(reply)
            android.util.Log.d("FitAI_Diag", "analyzeFood cleanReply: $cleanReply")
            val json = org.json.JSONObject(cleanReply)
            GemmaFoodAnalysis(
                calories = json.optDouble("calories", 150.0).toFloat(),
                protein = json.optDouble("protein", 10.0).toFloat(),
                carbs = json.optDouble("carbs", 12.0).toFloat(),
                fat = json.optDouble("fat", 3.0).toFloat(),
                isSuitable = json.optBoolean("suitable", true),
                advice = json.optString("advice", "這是本地 AI 估算的營養價值。請視實際烹調方式調整。"),
                reasoning = json.optString("reasoning", "無本地 AI 估算依據。"),
                thinking = thinkingText
            )
        } catch (e: Exception) {
            android.util.Log.e("FitAI_Diag", "analyzeFood JSON parse failed", e)
            val partialAnalysis = GemmaOutputParser.extractPartialFoodAnalysis(reply, thinkingText)
            if (partialAnalysis != null) {
                android.util.Log.w("FitAI_Diag", "analyzeFood used partial JSON recovery")
                return partialAnalysis
            }
            GemmaFoodAnalysis(
                calories = 200f,
                protein = 8f,
                carbs = 20f,
                fat = 6f,
                isSuitable = true,
                advice = "本地 Gemma 推論成功。請注意食物份量並搭配均衡飲食。",
                reasoning = "推論過程因 JSON 解析失敗而未顯示。",
                thinking = thinkingText
            )
        }
    }

    override suspend fun identifyFoodFromImage(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val inference = beginInference("identifyFoodFromImage", "bitmap=${bitmap.width}x${bitmap.height}")
        var locked = false
        try {
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} waiting for modelMutex")
            modelMutex.lock()
            locked = true
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} mutex acquired")
            val currentConversation = createConversationForInference(inference) ?: return@withContext "未知食物"
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val imageBytes = outputStream.toByteArray()

            val imageContent = Content.ImageBytes(imageBytes)
            val textContent = Content.Text(
                "你是一個飲食辨識助手，使用繁體中文。請只回答一個食物名稱，不要其他說明。" +
                "看看這張圖片，圖中有什麼食物？只回答食物的繁體中文名稱，例如：雞蛋、薯條、雞胸肉。"
            )

            // Contents constructor is `internal` — use reflection to bypass module boundary
            @Suppress("UNCHECKED_CAST")
            val contentsConstructor = Contents::class.java.getDeclaredConstructor(List::class.java)
            contentsConstructor.isAccessible = true
            val contents = contentsConstructor.newInstance(
                listOf<Content>(imageContent, textContent)
            ) as Contents

            val response = StringBuilder()
            currentConversation.sendMessageAsync(contents).collect { msg ->
                response.append(msg)
            }
            val result = response.toString().trim()
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} completed visionResult=${result.take(80)}")
            if (result.isBlank()) "未知食物" else result
        } catch (e: CancellationException) {
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} cancelled inside identifyFoodFromImage: ${e.message}")
            resetConversationAfterCancelledInference(inference)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("FitAI_GemmaReq", "GemmaInference#${inference.id} identifyFoodFromImage failed", e)
            resetReusableConversationIfRecoverableSessionError(inference, e)
            "未知食物"
        } finally {
            if (locked) {
                modelMutex.unlock()
                android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} mutex released")
            }
            finishInference(inference)
        }
    }

    private suspend fun beginInference(type: String, promptOrDetail: String): InferenceRequest {
        val currentJob = currentCoroutineContext()[Job]
            ?: throw IllegalStateException("Gemma inference must run in a coroutine")
        val id = inferenceIdGenerator.incrementAndGet()
        val startedAt = System.currentTimeMillis()
        val preview = promptOrDetail.toLogPreview()
        synchronized(inferenceLock) {
            val previousJob = activeInferenceJob
            val previousId = activeInferenceId
            val previousType = activeInferenceType
            val previousWasActive = previousJob?.isActive == true
            activeInferenceJob
                ?.takeIf { it != currentJob && it.isActive }
                ?.also {
                    android.util.Log.d(
                        "FitAI_GemmaReq",
                        "GemmaInference#$id cancelling previous #$previousId type=$previousType before starting type=$type"
                    )
                    it.cancel(CancellationException("Superseded by GemmaInference#$id type=$type"))
                }
            activeInferenceJob = currentJob
            activeInferenceId = id
            activeInferenceType = type
            activeInferenceStartedAt = startedAt
            android.util.Log.d(
                "FitAI_GemmaReq",
                "GemmaInference#$id start type=$type previousActiveBeforeCancel=$previousWasActive loadState=${_loadState.value} model=${_loadedModelName.value} preview=$preview"
            )
        }
        return InferenceRequest(id = id, type = type, job = currentJob, startedAt = startedAt)
    }

    private fun finishInference(inference: InferenceRequest) {
        val elapsedMs = System.currentTimeMillis() - inference.startedAt
        synchronized(inferenceLock) {
            if (activeInferenceJob == inference.job) {
                activeInferenceJob = null
                activeInferenceId = null
                activeInferenceType = null
                activeInferenceStartedAt = 0L
                android.util.Log.d(
                    "FitAI_GemmaReq",
                    "GemmaInference#${inference.id} finish type=${inference.type} elapsedMs=$elapsedMs clearedActive=true"
                )
            } else {
                android.util.Log.d(
                    "FitAI_GemmaReq",
                    "GemmaInference#${inference.id} finish type=${inference.type} elapsedMs=$elapsedMs clearedActive=false active=#${activeInferenceId}"
                )
            }
        }
    }

    private fun cancelActiveInference(reason: String) {
        synchronized(inferenceLock) {
            android.util.Log.d(
                "FitAI_GemmaReq",
                "cancelActiveInference reason=$reason active=#${activeInferenceId} type=$activeInferenceType activeForMs=${if (activeInferenceStartedAt == 0L) 0 else System.currentTimeMillis() - activeInferenceStartedAt}"
            )
            activeInferenceJob?.cancel(CancellationException(reason))
            activeInferenceJob = null
            activeInferenceId = null
            activeInferenceType = null
            activeInferenceStartedAt = 0L
        }
    }

    private fun String.toLogPreview(maxLength: Int = 120): String {
        return replace(Regex("\\s+"), " ")
            .trim()
            .let { if (it.length > maxLength) it.take(maxLength) + "..." else it }
    }

    private fun String.inferGemmaTaskType(): String {
        return when {
            contains("今日熱量目標") && contains("今日三大營養素") -> "dashboardAdvice"
            contains("professional fitness coach") && contains("weekly workout plan") -> "workoutPlan"
            contains("workout plan JSON") && contains("motivating summary") -> "workoutSummary"
            contains("professional nutritionist") && contains("calorie count") -> "foodAnalysis"
            contains("健康與營養顧問") || contains("健康飲食與運動助理") || contains("健康助理") -> "chat"
            else -> "unknown"
        }
    }

    private fun resetConversationAfterCancelledInference(inference: InferenceRequest) {
        android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} cancelled; keeping reusable conversation open")
    }

    private fun resetReusableConversationIfRecoverableSessionError(inference: InferenceRequest, throwable: Throwable) {
        val messages = mutableListOf<String>()
        var current: Throwable? = throwable
        while (current != null) {
            current.message?.let { messages += it }
            current = current.cause
        }
        val message = messages.joinToString(separator = " | ")
        if (!message.contains("Session is not prefilled yet", ignoreCase = true)) {
            return
        }

        android.util.Log.w(
            "FitAI_GemmaReq",
            "GemmaInference#${inference.id} resetting reusable conversation after recoverable session error: $message"
        )
        val currentConv = inferenceConversation
        inferenceConversation = null
        try {
            currentConv?.close()
        } catch (e: Exception) {
            android.util.Log.w("FitAI_GemmaReq", "GemmaInference#${inference.id} failed closing broken conversation", e)
        }
    }

    private var inferenceConversation: Conversation? = null

    private fun createConversationForInference(inference: InferenceRequest): Conversation? {
        inferenceConversation?.let {
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} reusing conversation")
            return it
        }

        val currentEngine = engine
        val currentConfig = conversationConfig
        if (currentEngine == null || currentConfig == null) {
            android.util.Log.w("FitAI_GemmaReq", "GemmaInference#${inference.id} cannot create one-shot conversation: engine/config unavailable")
            return null
        }

        return try {
            currentEngine.createConversation(currentConfig).also {
                inferenceConversation = it
                android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} created reusable conversation")
            }
        } catch (e: Exception) {
            android.util.Log.e("FitAI_GemmaReq", "GemmaInference#${inference.id} failed creating one-shot conversation", e)
            null
        }
    }

    private fun closeInferenceConversation(inference: InferenceRequest) {
        val current = inferenceConversation ?: return
        try {
            current.close()
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} closed one-shot conversation")
        } catch (e: Exception) {
            android.util.Log.w("FitAI_GemmaReq", "GemmaInference#${inference.id} failed closing one-shot conversation", e)
        } finally {
            inferenceConversation = null
        }
    }

    private fun closeResources() {
        try {
            inferenceConversation?.close()
        } catch (e: Exception) {}
        try {
            engine?.close()
        } catch (e: Exception) {}
        inferenceConversation = null
        conversationConfig = null
        engine = null
        _visionReady.value = false
    }

    private data class ModelInstance(
        val engine: Engine,
        val visionEnabled: Boolean
    )

    private fun createInitializedConversation(modelPath: String): ModelInstance {
        val maxTokens = runBlocking { userPreferenceStore.maxTokensFlow.first() }
        val useGpu = runBlocking { userPreferenceStore.useGpuFlow.first() }
        val systemPrompt = runBlocking { userPreferenceStore.systemPromptFlow.first() }
        val speculative = runBlocking { userPreferenceStore.enableSpeculativeFlow.first() }
        val thinking = runBlocking { userPreferenceStore.enableThinkingFlow.first() }

        // Apply speculative decoding experimental flag globally
        try {
            @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
            com.google.ai.edge.litertlm.ExperimentalFlags.enableSpeculativeDecoding = speculative
            android.util.Log.d("FitAI_Diag", "Applied enableSpeculativeDecoding = $speculative")
        } catch (e: Exception) {
            android.util.Log.w("FitAI_Diag", "Failed to apply speculative decoding experimental flag", e)
        }

        val conversationConfig = createConversationConfig(systemPrompt, thinking)

        // Backend candidate configurations list
        val configs = if (useGpu) {
            listOf(
                EngineConfig(modelPath = modelPath, backend = Backend.GPU(), visionBackend = Backend.GPU(), maxNumTokens = maxTokens) to true,
                EngineConfig(modelPath = modelPath, backend = Backend.GPU(), maxNumTokens = maxTokens) to false,
                EngineConfig(modelPath = modelPath, backend = Backend.CPU(), maxNumTokens = maxTokens) to false
            )
        } else {
            listOf(
                EngineConfig(modelPath = modelPath, backend = Backend.CPU(), visionBackend = Backend.GPU(), maxNumTokens = maxTokens) to true,
                EngineConfig(modelPath = modelPath, backend = Backend.CPU(), visionBackend = Backend.CPU(), maxNumTokens = maxTokens) to true,
                EngineConfig(modelPath = modelPath, backend = Backend.CPU(), maxNumTokens = maxTokens) to false
            )
        }

        var lastError: Exception? = null
        for ((config, visionEnabled) in configs) {
            var candidateEngine: Engine? = null
            try {
                android.util.Log.d("FitAI_Diag", "Trying config: backend=${config.backend}, vision=${config.visionBackend}, maxTokens=${config.maxNumTokens}")
                candidateEngine = Engine(config).also { it.initialize() }
                candidateEngine.createConversation(conversationConfig).close()
                android.util.Log.d("FitAI_GemmaReq", "Created conversation thinking=$thinking")
                this.conversationConfig = conversationConfig
                return ModelInstance(candidateEngine, visionEnabled)
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

    private fun createConversationConfig(systemPrompt: String, thinking: Boolean): ConversationConfig {
        @Suppress("UNCHECKED_CAST")
        val contentsConstructor = Contents::class.java.getDeclaredConstructor(List::class.java)
        contentsConstructor.isAccessible = true
        val systemInstructionContents = contentsConstructor.newInstance(
            listOf<Content>(Content.Text(systemPrompt))
        ) as Contents

        val topK = runBlocking { userPreferenceStore.topKFlow.first() }
        val topP = runBlocking { userPreferenceStore.topPFlow.first() }
        val temp = runBlocking { userPreferenceStore.temperatureFlow.first() }
        val samplerConfig = SamplerConfig(
            topK = topK,
            topP = topP.toDouble(),
            temperature = temp.toDouble(),
            seed = 0
        )

        val extra = mapOf<String, Any>(
            "enable_thinking" to thinking,
            "thinking_token_budget" to (if (thinking) 256 else 0)
        )

        android.util.Log.d("FitAI_Diag", extra.toString())

        return ConversationConfig(
            systemInstructionContents,
            emptyList(),
            emptyList(),
            samplerConfig,
            false,
            emptyList(),
            extra
        )
    }

}
