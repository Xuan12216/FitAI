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
import kotlinx.coroutines.flow.map
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

    private val _audioReady = MutableStateFlow(false)
    override val audioReady: StateFlow<Boolean> = _audioReady.asStateFlow()

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
    override val contextTokenLimit = userPreferenceStore.maxTokensFlow

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
            _audioReady.value = false
            return@withContext false
        }

        _loadState.value = ModelLoadState.Loading
        _loadedModelName.value = null
        _visionReady.value = false
        _audioReady.value = false

        return@withContext modelMutex.withLock {
            try {
                android.util.Log.d("FitAI_Diag", "GemmaLocalHelperImpl: Starting load...")
                closeResources()

                val modelInstance = createInitializedConversation(modelPath)

                engine = modelInstance.engine
                _visionReady.value = modelInstance.visionEnabled
                _audioReady.value = modelInstance.audioEnabled
                _loadedModelName.value = modelName
                _loadState.value = ModelLoadState.Loaded
                android.util.Log.d(
                    "FitAI_Diag",
                    "GemmaLocalHelperImpl: Loaded successfully! " +
                        "visionReady=${modelInstance.visionEnabled}, audioReady=${modelInstance.audioEnabled}"
                )
                true
            } catch (e: Exception) {
                android.util.Log.e("FitAI_Diag", "GemmaLocalHelperImpl: Load failed!", e)
                _loadedModelName.value = null
                _visionReady.value = false
                _audioReady.value = false
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
            activateInference(inference)
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} mutex acquired")
            val currentConversation = createConversationForInference(inference)
                ?: return@withContext "錯誤：Gemma 模型尚未載入。"
            try {
                val response = StringBuilder()
                currentConversation.sendMessageAsync(prompt).map { it.toString() }.requireAiResponse().collect { token ->
                    response.append(token)
                }
                android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} completed responseChars=${response.length}")
                response.toString()
            } catch (e: CancellationException) {
                android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} cancelled inside generateReply: ${e.message}")
                if (locked) cancelConversation(inference)
                throw e
            } catch (e: Exception) {
                android.util.Log.e("FitAI_GemmaReq", "GemmaInference#${inference.id} failed inside generateReply", e)
                    throw e
            }
        } finally {
            if (locked) {
                closeInferenceConversation(inference)
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
            activateInference(inference)
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} mutex acquired")
            val currentConversation = createConversationForInference(inference)
                ?: throw IllegalStateException("錯誤：Gemma 模型尚未載入。")
            currentConversation.sendMessageAsync(prompt).map { it.toString() }.requireAiResponse().collect { token ->
                val tokenText = token
                tokenCount += 1
                charCount += tokenText.length
                emit(tokenText)
            }
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} completed tokens=$tokenCount chars=$charCount")
        } catch (e: CancellationException) {
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} cancelled inside generateReplyFlow after tokens=$tokenCount chars=$charCount: ${e.message}")
            if (locked) cancelConversation(inference)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("FitAI_GemmaReq", "GemmaInference#${inference.id} failed inside generateReplyFlow after tokens=$tokenCount chars=$charCount", e)
            throw e
        } finally {
            if (locked) {
                closeInferenceConversation(inference)
                modelMutex.unlock()
                android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} mutex released")
            }
            finishInference(inference)
        }
    }.flowOn(Dispatchers.IO)

    override fun generateReplyWithMediaFlow(
        prompt: String,
        images: List<Bitmap>,
        audioBytes: ByteArray?,
    ): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        val mediaLabel = buildString {
            if (images.isNotEmpty()) append("images=${images.size}")
            if (audioBytes?.isNotEmpty() == true) {
                if (isNotEmpty()) append('+')
                append("audio")
            }
        }
        val inference = beginInference(
            "generateReplyWithMediaFlow/${prompt.inferGemmaTaskType()}",
            "media=$mediaLabel audio=${audioBytes?.takeIf { it.isNotEmpty() }?.let(::describeAudioBytes) ?: "none"} prompt=$prompt"
        )
        var locked = false
        var tokenCount = 0
        try {
            modelMutex.lock()
            locked = true
            activateInference(inference)
            val currentConversation = createConversationForInference(inference)
                ?: throw IllegalStateException("錯誤：Gemma 模型尚未載入。")
            val responseFlow = if (images.isEmpty() && audioBytes?.isNotEmpty() != true) {
                currentConversation.sendMessageAsync(prompt)
            } else {
                currentConversation.sendMessageAsync(createMediaContents(prompt, images, audioBytes))
            }
            responseFlow.map { it.toString() }.requireAiResponse().collect { token ->
                tokenCount += 1
                emit(token)
            }
            android.util.Log.d(
                "FitAI_GemmaReq",
                "GemmaInference#${inference.id} completed media=$mediaLabel tokens=$tokenCount"
            )
        } catch (e: CancellationException) {
            if (locked) cancelConversation(inference)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("FitAI_GemmaReq", "Gemma media inference failed", e)
            throw e
        } finally {
            if (locked) {
                closeInferenceConversation(inference)
                modelMutex.unlock()
            }
            finishInference(inference)
        }
    }.flowOn(Dispatchers.IO)

    private fun createMediaContents(
        prompt: String,
        images: List<Bitmap>,
        audioBytes: ByteArray?,
    ): Contents {
        val contents = buildList<Content> {
            images.forEach { add(Content.ImageBytes(bitmapToPngBytes(it))) }
            // Gemma 4 expects audio after the text content. Keep image before text,
            // then append the recorded WAV bytes after the instruction.
            add(Content.Text(prompt))
            audioBytes?.takeIf { it.isNotEmpty() }?.let { add(Content.AudioBytes(it)) }
        }

        return Contents.of(contents)
    }

    private fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return outputStream.toByteArray()
    }

    private fun describeAudioBytes(bytes: ByteArray): String {
        val riff = fourCc(bytes, 0)
        val wave = fourCc(bytes, 8)
        val format = fourCc(bytes, 12)
        val sampleRate = readLittleEndianInt(bytes, 24)
        val dataSize = readLittleEndianInt(bytes, 40)
        val nonZeroBytes = bytes.drop(44).count { it != 0.toByte() }
        return "bytes=${bytes.size},riff=$riff,wave=$wave,format=$format," +
            "sampleRate=$sampleRate,dataSize=$dataSize,nonZeroBytes=$nonZeroBytes"
    }

    private fun fourCc(bytes: ByteArray, offset: Int): String {
        if (offset < 0 || offset + 4 > bytes.size) return "?"
        return String(bytes, offset, 4, Charsets.US_ASCII)
    }

    private fun readLittleEndianInt(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > bytes.size) return -1
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
    }

    override suspend fun analyzeFood(foodName: String, portion: String, goal: String): GemmaFoodAnalysis {
        val maxTokens = userPreferenceStore.maxTokensFlow.first()
        return GemmaOutputParser.parseFoodAnalysis(
            generateReply(PromptBuilder.buildFoodAnalysisPrompt(foodName, portion, goal, maxTokens))
        )
    }

    override fun analyzeFoodFlow(foodName: String, portion: String, goal: String): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        val maxTokens = userPreferenceStore.maxTokensFlow.first()
        val prompt = PromptBuilder.buildFoodAnalysisPrompt(foodName, portion, goal, maxTokens)
        generateReplyFlow(prompt).collect { emit(it) }
    }

    override suspend fun identifyFoodFromImage(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val inference = beginInference("identifyFoodFromImage", "bitmap=${bitmap.width}x${bitmap.height}")
        var locked = false
        try {
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} waiting for modelMutex")
            modelMutex.lock()
            locked = true
            activateInference(inference)
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} mutex acquired")
            val currentConversation = createConversationForInference(inference) ?: return@withContext "未知食物"
            val imageBytes = bitmapToPngBytes(bitmap)

            val imageContent = Content.ImageBytes(imageBytes)
            val textContent = Content.Text(
                "你是一個飲食辨識助手，使用繁體中文。請只回答一個食物名稱，不要其他說明。" +
                "看看這張圖片，圖中有什麼食物？只回答食物的繁體中文名稱，例如：雞蛋、薯條、雞胸肉。"
            )

            val contents = Contents.of(listOf<Content>(imageContent, textContent))

            val response = StringBuilder()
            currentConversation.sendMessageAsync(contents).map { it.toString() }.requireAiResponse().collect { msg ->
                response.append(msg)
            }
            val result = response.toString().trim()
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} completed visionResult=${result.take(80)}")
            if (result.isBlank()) "未知食物" else result
        } catch (e: CancellationException) {
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} cancelled inside identifyFoodFromImage: ${e.message}")
            if (locked) cancelConversation(inference)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("FitAI_GemmaReq", "GemmaInference#${inference.id} identifyFoodFromImage failed", e)
            "未知食物"
        } finally {
            if (locked) {
                closeInferenceConversation(inference)
                modelMutex.unlock()
                android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} mutex released")
            }
            finishInference(inference)
        }
    }

    override fun identifyFoodFromImageFlow(bitmap: Bitmap): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        val inference = beginInference("identifyFoodFromImageFlow", "bitmap=${bitmap.width}x${bitmap.height}")
        var locked = false
        try {
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} waiting for modelMutex")
            modelMutex.lock()
            locked = true
            activateInference(inference)
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} mutex acquired")
            val currentConversation = createConversationForInference(inference)
                ?: throw IllegalStateException("Gemma model is not loaded")

            val imageBytes = bitmapToPngBytes(bitmap)

            val imageContent = Content.ImageBytes(imageBytes)
            val textContent = Content.Text(
                "Identify the main food in this image. Output one concise Traditional Chinese food name only. " +
                    "If no food is visible, output 無食物."
            )

            val contents = Contents.of(listOf<Content>(imageContent, textContent))

            var tokenCount = 0
            currentConversation.sendMessageAsync(contents).map { it.toString() }.requireAiResponse().collect { token ->
                tokenCount += 1
                emit(token)
            }
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} completed visionFlow tokens=$tokenCount")
        } catch (e: CancellationException) {
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} cancelled inside identifyFoodFromImageFlow: ${e.message}")
            if (locked) cancelConversation(inference)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("FitAI_GemmaReq", "GemmaInference#${inference.id} identifyFoodFromImageFlow failed", e)
            throw e
        } finally {
            if (locked) {
                closeInferenceConversation(inference)
                modelMutex.unlock()
                android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} mutex released")
            }
            finishInference(inference)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun beginInference(type: String, promptOrDetail: String): InferenceRequest {
        val currentJob = currentCoroutineContext()[Job]
            ?: throw IllegalStateException("Gemma inference must run in a coroutine")
        val id = inferenceIdGenerator.incrementAndGet()
        val startedAt = System.currentTimeMillis()
        android.util.Log.d("FitAI_GemmaReq", "GemmaInference#$id queued type=$type prompt=${promptOrDetail.toLogPreview()}")
        return InferenceRequest(id = id, type = type, job = currentJob, startedAt = startedAt)
    }

    // Register only after acquiring the mutex. Waiting requests never cancel the
    // owner, and model reloads can cancel the actual running request.
    private fun activateInference(inference: InferenceRequest) {
        synchronized(inferenceLock) {
            activeInferenceJob = inference.job
            activeInferenceId = inference.id
            activeInferenceType = inference.type
            activeInferenceStartedAt = inference.startedAt
        }
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

    private fun cancelConversation(inference: InferenceRequest) {
        try {
            inferenceConversation?.cancelProcess()
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} native generation cancelled")
        } catch (e: Exception) {
            android.util.Log.w("FitAI_GemmaReq", "Failed to cancel native generation", e)
        }
    }

    private var inferenceConversation: Conversation? = null

    private fun createConversationForInference(inference: InferenceRequest): Conversation? {
        if (_loadState.value != ModelLoadState.Loaded) return null
        val currentEngine = engine ?: return null
        val currentConfig = conversationConfig ?: return null
        closeInferenceConversation(inference)
        return currentEngine.createConversation(currentConfig).also {
            inferenceConversation = it
            android.util.Log.d("FitAI_GemmaReq", "GemmaInference#${inference.id} created fresh conversation")
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
        _audioReady.value = false
    }

    private data class ModelInstance(
        val engine: Engine,
        val visionEnabled: Boolean,
        val audioEnabled: Boolean,
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
                Triple(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.GPU(),
                        visionBackend = Backend.GPU(),
                        audioBackend = Backend.CPU(),
                        maxNumTokens = maxTokens,
                    ),
                    true,
                    true,
                ),
                Triple(
                    EngineConfig(modelPath = modelPath, backend = Backend.GPU(), visionBackend = Backend.GPU(), maxNumTokens = maxTokens),
                    true,
                    false,
                ),
                Triple(
                    EngineConfig(modelPath = modelPath, backend = Backend.GPU(), maxNumTokens = maxTokens),
                    false,
                    false,
                ),
                Triple(
                    EngineConfig(modelPath = modelPath, backend = Backend.CPU(), maxNumTokens = maxTokens),
                    false,
                    false,
                ),
            )
        } else {
            listOf(
                Triple(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        visionBackend = Backend.GPU(),
                        audioBackend = Backend.CPU(),
                        maxNumTokens = maxTokens,
                    ),
                    true,
                    true,
                ),
                Triple(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        visionBackend = Backend.CPU(),
                        audioBackend = Backend.CPU(),
                        maxNumTokens = maxTokens,
                    ),
                    true,
                    true,
                ),
                Triple(
                    EngineConfig(modelPath = modelPath, backend = Backend.CPU(), visionBackend = Backend.GPU(), maxNumTokens = maxTokens),
                    true,
                    false,
                ),
                Triple(
                    EngineConfig(modelPath = modelPath, backend = Backend.CPU(), visionBackend = Backend.CPU(), maxNumTokens = maxTokens),
                    true,
                    false,
                ),
                Triple(
                    EngineConfig(modelPath = modelPath, backend = Backend.CPU(), maxNumTokens = maxTokens),
                    false,
                    false,
                ),
            )
        }

        var lastError: Exception? = null
        for ((config, visionEnabled, audioEnabled) in configs) {
            var candidateEngine: Engine? = null
            try {
                android.util.Log.d(
                    "FitAI_Diag",
                    "Trying config: backend=${config.backend}, " +
                        "vision=${config.visionBackend}, audio=${config.audioBackend}, " +
                        "maxTokens=${config.maxNumTokens}"
                )
                candidateEngine = Engine(config).also { it.initialize() }
                candidateEngine.createConversation(conversationConfig).close()
                android.util.Log.d("FitAI_GemmaReq", "Created conversation thinking=$thinking")
                this.conversationConfig = conversationConfig
                return ModelInstance(candidateEngine, visionEnabled, audioEnabled)
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
        val systemInstructionContents = Contents.of(listOf<Content>(Content.Text(systemPrompt)))

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
