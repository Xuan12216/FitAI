package com.xuan.fitai.ui.chat

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

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    val isThinking: StateFlow<Boolean> = combine(_isGenerating, _streamingMessage) { generating, streaming ->
        generating && streaming == null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg.asStateFlow()

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        viewModelScope.launch {
            _errorMsg.value = null
            if (gemmaLoadState.value != ModelLoadState.Loaded) {
                _errorMsg.value = "請先至「模型設定」頁面載入 Gemma AI 模型"
                return@launch
            }

            // Insert User Message
            val userMsgObj = ChatMessage(role = "user", content = userMessage)
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
                    userMessage = userMessage
                )

                var currentText = ""
                gemmaHelper.generateReplyFlow(fullPrompt).collect { token ->
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
