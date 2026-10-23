package com.xuan.fitai.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.cancelAndJoin
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.xuan.fitai.ai.GemmaLocalHelper
import com.xuan.fitai.ai.GemmaOutputParser
import com.xuan.fitai.ai.PromptBuilder
import com.xuan.fitai.data.local.ChatDao
import com.xuan.fitai.data.model.ChatMessage
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.data.model.UserProfile
import com.xuan.fitai.data.repository.MealRepository
import com.xuan.fitai.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val userRepository: UserRepository,
    private val mealRepository: MealRepository,
    private val chatDao: ChatDao,
    private val gemmaHelper: GemmaLocalHelper
) : ViewModel() {

    val userProfile: StateFlow<UserProfile> = userRepository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val todayMeals = mealRepository.getMealsForDay(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _streamingMessage = MutableStateFlow<ChatMessage?>(null)

    val chatMessages: StateFlow<List<ChatMessage>> = combine(
        chatDao.getAllMessages(),
        _streamingMessage
    ) { dbMessages, streamingMsg ->
        if (streamingMsg != null) {
            dbMessages + streamingMsg
        } else {
            dbMessages
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var draftImages by mutableStateOf<List<Bitmap>>(emptyList())
    var draftAudio by mutableStateOf<ByteArray?>(null)
    val gemmaLoadState = gemmaHelper.loadState
    val visionReady = gemmaHelper.visionReady
    val audioReady = gemmaHelper.audioReady

    private var generationJob: Job? = null

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    val isThinking: StateFlow<Boolean> = combine(_isGenerating, _streamingMessage) { generating, streaming ->
        generating && streaming == null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg.asStateFlow()

    suspend fun saveMealFromChat(name: String, calories: Float, protein: Float, carbs: Float, fat: Float) {
        require(name.isNotBlank() && listOf(calories, protein, carbs, fat).all { it.isFinite() && it >= 0 })
        mealRepository.insertMeal(com.xuan.fitai.data.model.Meal(name = name, calories = calories, protein = protein, carbs = carbs, fat = fat))
    }

    fun sendMessage(
        userMessage: String,
        images: List<Bitmap> = emptyList(),
        audioBytes: ByteArray? = null,
    ): Boolean {
        if (userMessage.isBlank() && images.isEmpty() && audioBytes?.isNotEmpty() != true) return false
        if (_isGenerating.value) return false

        _errorMsg.value = null
        if (gemmaLoadState.value != ModelLoadState.Loaded) {
            _errorMsg.value = if (gemmaLoadState.value == ModelLoadState.Loading) {
                "AI 助理正在背景暖機，準備完成後即可傳送訊息"
            } else {
                "請先至「模型設定」頁面載入 Gemma AI 模型"
            }
            return false
        }

        if (images.isNotEmpty() && !visionReady.value) {
            _errorMsg.value = "目前載入的 Gemma 模型不支援圖片辨識"
            return false
        }
        if (audioBytes?.isNotEmpty() == true && !audioReady.value) {
            _errorMsg.value = "目前載入的 Gemma 模型不支援語音辨識"
            return false
        }

        _isGenerating.value = true
        generationJob = viewModelScope.launch {
            var currentText = ""
            try {
                val hasAudio = audioBytes?.isNotEmpty() == true
                val mediaInstruction = when {
                    images.isNotEmpty() && hasAudio ->
                        "請實際查看附上的圖片並聆聽附上的語音。先轉寫語音，再結合圖片內容回答。"
                    images.isNotEmpty() ->
                        "請實際查看附上的圖片，描述你看到的內容並回答。"
                    hasAudio ->
                        "請實際聆聽附上的語音，先轉寫語音內容，再根據內容回答。不要假設沒有語音。"
                    else -> ""
                }
                val promptText = userMessage.trim().ifBlank {
                    mediaInstruction.ifBlank { "請用繁體中文回答。" }
                }
                val promptForModel = if (mediaInstruction.isNotBlank() && userMessage.isNotBlank()) {
                    "$mediaInstruction\n${userMessage.trim()}"
                } else {
                    promptText
                }
                val storedUserContent = buildString {
                    if (userMessage.isNotBlank()) {
                        append(userMessage.trim())
                    } else if (images.isNotEmpty()) {
                        append("圖片")
                    } else if (audioBytes?.isNotEmpty() == true) {
                        append("語音")
                    }
                }.ifBlank { promptText }

                val contextTokens = gemmaHelper.contextTokenLimit.first()
                val history = PromptBuilder.recentConversation(
                    chatDao.getAllMessages().first().map { it.role to it.content },
                    contextTokens,
                )
                // Insert User Message
                val userMsgObj = ChatMessage(
                    role = "user",
                    content = storedUserContent,
                    audioBytes = audioBytes,
                    imageBytes = ChatImageCodec.encode(images),
                )
                chatDao.insertMessage(userMsgObj)

                // Compute current day totals
                val mealsList = todayMeals.value
                val cal = mealsList.sumOf { it.calories.toDouble() }.toFloat()
                val prot = mealsList.sumOf { it.protein.toDouble() }.toFloat()
                val carb = mealsList.sumOf { it.carbs.toDouble() }.toFloat()
                val fat = mealsList.sumOf { it.fat.toDouble() }.toFloat()

                // Build context-aware prompt
                val fullPrompt = PromptBuilder.buildAdvicePrompt(
                    profile = userProfile.value,
                    todayCalories = cal,
                    todayProtein = prot,
                    todayCarbs = carb,
                    todayFat = fat,
                    userMessage = promptForModel,
                    recentConversation = history,
                    maxTokens = contextTokens,
                )

                currentText = ""
                val responseFlow = if (images.isNotEmpty() || audioBytes?.isNotEmpty() == true) {
                    gemmaHelper.generateReplyWithMediaFlow(
                        prompt = fullPrompt,
                        images = images,
                        audioBytes = audioBytes,
                    )
                } else {
                    gemmaHelper.generateReplyFlow(fullPrompt)
                }
                responseFlow.collect { token ->
                    if (_streamingMessage.value == null) {
                        _streamingMessage.value = ChatMessage(role = "assistant", content = "")
                    }
                    currentText += token
                    _streamingMessage.value = ChatMessage(role = "assistant", content = currentText)
                    if (GemmaOutputParser.detectRepetitionLoop(currentText) != null) {
                        throw RepetitionLoopException()
                    }
                }

                val finalContent = GemmaOutputParser.pruneRepetitionLoop(currentText).trim()
                check(finalContent.isNotBlank()) { "模型沒有回覆，請重試或增加上下文容量。" }
                chatDao.insertMessage(ChatMessage(role = "assistant", content = finalContent))
            } catch (e: RepetitionLoopException) {
                Log.w("FitAI_ChatVM", "Repetition loop detected during generation; pruning and finishing early.")
                val pruned = GemmaOutputParser.pruneRepetitionLoop(currentText).trim()
                if (pruned.isNotBlank()) {
                    chatDao.insertMessage(ChatMessage(role = "assistant", content = pruned))
                }
            } catch (e: CancellationException) {
                _errorMsg.value = "已停止生成，可以重新傳送。"
                throw e
            } catch (e: Exception) {
                _errorMsg.value = "Gemma 回覆失敗: ${e.localizedMessage}"
            } finally {
                _streamingMessage.value = null
                _isGenerating.value = false
                generationJob = null
            }
        }
        return true
    }

    fun stopGeneration() {
        generationJob?.cancel()
    }

    fun clearChat() {
        viewModelScope.launch {
            generationJob?.cancelAndJoin()
            _streamingMessage.value = null
            _isGenerating.value = false
            chatDao.clearChatHistory()
        }
    }

    class Factory(
        private val userRepository: UserRepository,
        private val mealRepository: MealRepository,
        private val chatDao: ChatDao,
        private val gemmaHelper: GemmaLocalHelper
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(userRepository, mealRepository, chatDao, gemmaHelper) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private class RepetitionLoopException : CancellationException("Repetition loop detected")
}
