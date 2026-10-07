package com.racha.ai.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.data.api.GeminiService
import com.racha.ai.domain.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val hasApiKey: Boolean = true
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val securePrefs = SecurePreferences(application)
    private var geminiService: GeminiService? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        initializeGemini()
    }

    private fun initializeGemini() {
        val apiKey = securePrefs.getApiKey()
        if (apiKey.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                hasApiKey = false,
                messages = listOf(
                    Message(
                        text = "لم يتم إدخال مفتاح Gemini API بعد. الرجاء فتح الإعدادات وإدخال المفتاح.",
                        isFromUser = false
                    )
                )
            )
        } else {
            geminiService = GeminiService(apiKey)
            _uiState.value = _uiState.value.copy(
                hasApiKey = true,
                messages = listOf(
                    Message(
                        text = "مرحباً بك! أنا Racha، مساعدك الشخصي. كيف أساعدك اليوم؟",
                        isFromUser = false
                    )
                )
            )
        }
    }

    fun reloadApiKey() {
        val apiKey = securePrefs.getApiKey()
        if (!apiKey.isNullOrBlank()) {
            geminiService = GeminiService(apiKey)
            _uiState.value = _uiState.value.copy(
                hasApiKey = true,
                messages = listOf(
                    Message(
                        text = "تم تحديث المفتاح. جاهز للمحادثة!",
                        isFromUser = false
                    )
                )
            )
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
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + Message(
                    text = "لا يمكن الإرسال: مفتاح API غير موجود.",
                    isFromUser = false,
                    isError = true
                ),
                inputText = ""
            )
            return
        }

        val userMessage = Message(text = text, isFromUser = true)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            inputText = "",
            isSending = true
        )

        viewModelScope.launch {
            val result = service.sendMessage(text)
            result.fold(
                onSuccess = { reply ->
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + Message(
                            text = reply,
                            isFromUser = false
                        ),
                        isSending = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + Message(
                            text = "خطأ: ${error.message ?: "غير معروف"}",
                            isFromUser = false,
                            isError = true
                        ),
                        isSending = false
                    )
                }
            )
        }
    }

    fun clearHistory() {
        geminiService?.clearHistory()
        _uiState.value = _uiState.value.copy(
            messages = listOf(
                Message(
                    text = "تم مسح المحادثة. كيف أساعدك؟",
                    isFromUser = false
                )
            )
        )
    }
}
