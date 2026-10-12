package com.racha.ai.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.data.api.GeminiService
import com.racha.ai.data.local.ChatMessageEntity
import com.racha.ai.data.repository.ChatMemoryRepository
import com.racha.ai.data.repository.UserProfileRepository
import com.racha.ai.domain.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val hasApiKey: Boolean = true,
    val isLoadingHistory: Boolean = true,
    val hasProfile: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val securePrefs = SecurePreferences(application)
    private val memory = ChatMemoryRepository(application)
    private val profileRepo = UserProfileRepository(application)
    private var geminiService: GeminiService? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var welcomeInitialized = false

    init {
        initializeGemini()
        observeHistory()
        checkProfile()
    }

    private fun initializeGemini() {
        val apiKey = securePrefs.getApiKey()
        if (apiKey.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                hasApiKey = false,
                isLoadingHistory = false
            )
        } else {
            geminiService = GeminiService(apiKey)
            _uiState.value = _uiState.value.copy(hasApiKey = true)
        }
    }

    private fun checkProfile() {
        viewModelScope.launch {
            val isFilled = profileRepo.isFilled()
            _uiState.value = _uiState.value.copy(hasProfile = isFilled)
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            memory.getConversation().collectLatest { entities ->
                val messages = entities.map { entity ->
                    Message(
                        id = entity.id,
                        text = entity.text,
                        isFromUser = entity.isFromUser,
                        timestamp = entity.timestamp,
                        isError = entity.isError
                    )
                }

                if (messages.isEmpty() && !welcomeInitialized && _uiState.value.hasApiKey) {
                    welcomeInitialized = true
                    launch {
                        val welcomeText = "أهلاً بك! أنا Racha، رفيقك الشخصي. كيف حالك اليوم؟"
                        val welcome = Message(text = welcomeText, isFromUser = false)
                        memory.saveMessage(
                            ChatMessageEntity(
                                id = welcome.id,
                                text = welcome.text,
                                isFromUser = false,
                                isError = false,
                                timestamp = welcome.timestamp
                            )
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        messages = messages,
                        isLoadingHistory = false
                    )
                }
            }
        }
    }

    fun onInputChange(newText: String) {
        _uiState.value = _uiState.value.copy(inputText = newText)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isSending) return

        val service = geminiService
        if (service == null) {
            viewModelScope.launch {
                saveMessageEntity(
                    Message(text = "لم يتم إدخال مفتاح API بعد.", isFromUser = false, isError = true)
                )
            }
            _uiState.value = _uiState.value.copy(inputText = "")
            return
        }

        viewModelScope.launch {
            val userMessage = Message(text = text, isFromUser = true)
            saveMessageEntity(userMessage)
            _uiState.value = _uiState.value.copy(inputText = "", isSending = true)

            val recent = memory.getRecentForContext()
            val history = recent.filter { it.id != userMessage.id }

            // ✅ الملف الشخصي
            val profileContext = profileRepo.getPromptContext()

            val result = service.sendMessageWithContext(history, text, profileContext)

            result.fold(
                onSuccess = { reply ->
                    saveMessageEntity(Message(text = reply, isFromUser = false))
                    _uiState.value = _uiState.value.copy(isSending = false)
                },
                onFailure = { error ->
                    val errorMessage = error.message ?: ""
                    val userFriendly = when {
                        errorMessage.contains("503") ||
                        errorMessage.contains("UNAVAILABLE") -> "⚠️ Gemini مزدحم حالياً. حاول بعد قليل."
                        errorMessage.contains("API key") ||
                        errorMessage.contains("API_KEY") -> "⚠️ المفتاح غير صالح."
                        errorMessage.contains("quota") -> "⚠️ استُهلكت الحصة اليومية."
                        else -> "⚠️ خطأ: ${error.message ?: "غير معروف"}"
                    }
                    saveMessageEntity(Message(text = userFriendly, isFromUser = false, isError = true))
                    _uiState.value = _uiState.value.copy(isSending = false)
                }
            )
        }
    }

    private suspend fun saveMessageEntity(message: Message) {
        memory.saveMessage(
            ChatMessageEntity(
                id = message.id,
                text = message.text,
                isFromUser = message.isFromUser,
                isError = message.isError,
                timestamp = message.timestamp
            )
        )
    }

    fun clearHistory() {
        viewModelScope.launch {
            memory.clearAll()
        }
    }

    /**
     * إعادة تحميل حالة الملف الشخصي (بعد رجوع المستخدم من تعديله).
     */
    fun refreshProfileStatus() {
        checkProfile()
    }
}
