package com.racha.ai.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.domain.model.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false
)

class ChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            messages = listOf(
                Message(
                    text = "مرحباً بك! أنا Racha، مساعدك الشخصي. كيف أساعدك اليوم؟",
                    isFromUser = false
                )
            )
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onInputChange(newText: String) {
        _uiState.value = _uiState.value.copy(inputText = newText)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isSending) return

        val userMessage = Message(text = text, isFromUser = true)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            inputText = "",
            isSending = true
        )

        viewModelScope.launch {
            delay(800)
            val replyMessage = Message(
                text = "هذا رد تجريبي. في الجلسة القادمة سيتم الاتصال بـ Gemini فعلياً. رسالتك كانت: \"$text\"",
                isFromUser = false
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + replyMessage,
                isSending = false
            )
        }
    }
}
