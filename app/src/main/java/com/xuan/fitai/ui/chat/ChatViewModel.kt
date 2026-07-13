package com.xuan.fitai.ui.chat

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xuan.fitai.ai.GemmaLocalHelper
import com.xuan.fitai.ai.PromptBuilder
import com.xuan.fitai.data.local.ChatDao
import com.xuan.fitai.data.model.ChatMessage
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.data.model.UserProfile
import com.xuan.fitai.data.repository.MealRepository
import com.xuan.fitai.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
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

    val gemmaLoadState = gemmaHelper.loadState
    val visionReady = gemmaHelper.visionReady
    val audioReady = gemmaHelper.audioReady

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    val isThinking: StateFlow<Boolean> = combine(_isGenerating, _streamingMessage) { generating, streaming ->
        generating && streaming == null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg.asStateFlow()

    fun sendMessage(
        userMessage: String,
        image: Bitmap? = null,
        audioBytes: ByteArray? = null,
    ) {
        if (userMessage.isBlank() && image == null && audioBytes?.isNotEmpty() != true) return
        if (_isGenerating.value) return

        viewModelScope.launch {
            _errorMsg.value = null
            if (gemmaLoadState.value != ModelLoadState.Loaded) {
                _errorMsg.value = "請先至「模型設定」頁面載入 Gemma AI 模型"
                return@launch
            }

            if (image != null && !visionReady.value) {
                _errorMsg.value = "目前載入的 Gemma 模型不支援圖片辨識"
                return@launch
            }
            if (audioBytes?.isNotEmpty() == true && !audioReady.value) {
                _errorMsg.value = "目前載入的 Gemma 模型不支援語音辨識"
                return@launch
            }

            val hasAudio = audioBytes?.isNotEmpty() == true
            val mediaInstruction = when {
                image != null && hasAudio ->
                    "請實際查看附上的圖片並聆聽附上的語音。先轉寫語音，再結合圖片內容回答。"
                image != null ->
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
                } else if (image != null) {
                    append("圖片")
                } else if (audioBytes?.isNotEmpty() == true) {
                    append("語音")
                }
            }.ifBlank { promptText }

            // Insert User Message
            val userMsgObj = ChatMessage(
                role = "user",
                content = storedUserContent,
                audioBytes = audioBytes,
            )
            chatDao.insertMessage(userMsgObj)

            _isGenerating.value = true

            try {
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
                    userMessage = promptForModel
                )

                var currentText = ""
                val responseFlow = if (image != null || audioBytes?.isNotEmpty() == true) {
                    gemmaHelper.generateReplyWithMediaFlow(
                        prompt = fullPrompt,
                        image = image,
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
                }

                if (currentText.isNotEmpty()) {
                    chatDao.insertMessage(ChatMessage(role = "assistant", content = currentText))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMsg.value = "Gemma 回覆失敗: ${e.localizedMessage}"
            } finally {
                _streamingMessage.value = null
                _isGenerating.value = false
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
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
}
