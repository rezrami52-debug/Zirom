package com.racha.ai.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.core.apps.AppResolver
import com.racha.ai.core.apps.IntentHandler
import com.racha.ai.core.apps.PendingAction
import com.racha.ai.core.contacts.ContactResolver
import com.racha.ai.core.shizuku.ShizukuShellRunner
import com.racha.ai.data.gemini.GeminiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val pendingChooser: IntentHandler.HandlerResult.ShowChooser? = null,
    val inputText: String = "",
    val hasApiKey: Boolean = false,
    val pendingSpecialTool: String? = null
)

class ChatViewModel(
    private val intentHandler: IntentHandler,
    private val geminiService: GeminiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText
        if (text.isBlank()) return
        _uiState.update { it.copy(inputText = "") }
        sendMessage(text)
    }

    fun onSpecialToolCompleted(success: Boolean, message: String) {
        _uiState.update { it.copy(pendingSpecialTool = null) }
    }

    fun onSpecialToolCancelled() {
        _uiState.update { it.copy(pendingSpecialTool = null) }
    }

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isBlank()) return

        appendMessage(ChatMessage(text = trimmed, isFromUser = true))

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                when (val result = intentHandler.handle(trimmed)) {
                    is IntentHandler.HandlerResult.DirectReply -> {
                        appendMessage(ChatMessage(text = result.message, isFromUser = false))
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    is IntentHandler.HandlerResult.ShowChooser -> {
                        _uiState.update { it.copy(isLoading = false, pendingChooser = result) }
                    }
                    is IntentHandler.HandlerResult.Error -> {
                        appendMessage(ChatMessage(text = result.message, isFromUser = false))
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    is IntentHandler.HandlerResult.NeedsCorrection -> {
                        appendMessage(ChatMessage(text = "سيدي، هل يمكنك توضيح الأمر أكثر بخصوص: '${result.text}'؟", isFromUser = false))
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    is IntentHandler.HandlerResult.Unknown -> {
                        forwardToGemini(trimmed)
                    }
                }
            } catch (e: Exception) {
                appendMessage(ChatMessage(text = "عذراً سيدي، واجهت خطأ داخلي أثناء معالجة طلبك.", isFromUser = false))
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun forwardToGemini(prompt: String) {
        try {
            val response = geminiService.generateResponse(prompt)
            appendMessage(ChatMessage(text = response, isFromUser = false))
        } catch (e: Exception) {
            appendMessage(
                ChatMessage(
                    text = "سيدي، يبدو أن هناك تعثراً في الاتصال بالخادم السحابي. يرجى التحقق من الشبكة.",
                    isFromUser = false
                )
            )
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onAppChosen(app: AppResolver.InstalledApp, pendingAction: PendingAction) {
        val reply = intentHandler.executeChoice(app, pendingAction)
        appendMessage(ChatMessage(text = reply, isFromUser = false))
        _uiState.update { it.copy(pendingChooser = null) }
    }

    fun dismissChooser() {
        _uiState.update { it.copy(pendingChooser = null) }
    }

    private fun appendMessage(msg: ChatMessage) {
        _uiState.update { state ->
            state.copy(messages = state.messages + msg)
        }
    }
}
