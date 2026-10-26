package com.racha.ai.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.core.apps.AppResolver
import com.racha.ai.core.apps.IntentHandler
import com.racha.ai.core.apps.PendingAction
import com.racha.ai.core.contacts.ContactResolver
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.core.web.WebSearchEngine
import com.racha.ai.data.api.GeminiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val hasApiKey: Boolean = false,
    val pendingSpecialTool: String? = null,
    val pendingChooser: IntentHandler.HandlerResult.ShowChooser? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val securePrefs = SecurePreferences(application)
    private val appResolver = AppResolver(application)
    private val contactResolver = ContactResolver(application)
    private val intentHandler = IntentHandler(application, appResolver, contactResolver)

    init {
        _uiState.update {
            it.copy(
                messages = emptyList(),
                hasApiKey = !securePrefs.getApiKey().isNullOrBlank()
            )
        }
    }

    fun startNewChat() {
        _uiState.update { it.copy(messages = emptyList(), inputText = "") }
    }

    fun onInputChange(newText: String) {
        _uiState.update { it.copy(inputText = newText) }
    }

    fun onSpecialToolCompleted(success: Boolean, msg: String) {
        _uiState.update { it.copy(pendingSpecialTool = null) }
        appendMessage(ChatMessage(text = msg, isFromUser = false))
    }

    fun onSpecialToolCancelled() {
        _uiState.update { it.copy(pendingSpecialTool = null) }
    }

    private fun appendMessage(message: ChatMessage) {
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    private fun updateLastMessage(newText: String) {
        _uiState.update { state ->
            if (state.messages.isEmpty()) return@update state
            val updated = state.messages.toMutableList()
            val last = updated.last()
            updated[updated.lastIndex] = last.copy(text = newText)
            state.copy(messages = updated)
        }
    }

    private fun removeLastMessage() {
        _uiState.update { state ->
            if (state.messages.isEmpty()) return@update state
            state.copy(messages = state.messages.dropLast(1))
        }
    }

    fun sendMessage() {
        val userText = _uiState.value.inputText
        _uiState.update { it.copy(inputText = "") }

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
                        handleGeminiAndSmartQuery(trimmed)
                    }
                }
            } catch (e: Exception) {
                appendMessage(ChatMessage(text = "عذراً سيدي، واجهت مشكلة في معالجة طلبك.", isFromUser = false))
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun handleGeminiAndSmartQuery(query: String) {
        val apiKey = securePrefs.getApiKey()
        if (!apiKey.isNullOrBlank()) {
            try {
                // تجميع سجل المحادثة السابق لتغذية ذاكرة الذكاء الاصطناعي
                val history = _uiState.value.messages.dropLast(1).map { msg ->
                    Pair(if (msg.isFromUser) "user" else "model", msg.text)
                }

                val geminiService = GeminiService(apiKey)
                
                // إضافة رسالة فارغة للمساعد للبدء في ملئها بالبث المباشر
                appendMessage(ChatMessage(text = "", isFromUser = false))
                _uiState.update { it.copy(isLoading = false) }

                val fullResponse = StringBuilder()
                var receivedAnyChunk = false

                geminiService.generateResponseStream(query, history).collect { chunk ->
                    receivedAnyChunk = true
                    fullResponse.append(chunk)
                    updateLastMessage(fullResponse.toString())
                }

                if (receivedAnyChunk && fullResponse.isNotBlank()) {
                    return
                } else {
                    removeLastMessage()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                removeLastMessage()
            }
        }

        // البحث على الويب كخيار بديل إذا لم يتوفر مفتاح API أو فشل الاتصال
        try {
            _uiState.update { it.copy(isLoading = true) }
            val webResponse = WebSearchEngine.searchWeb(query)
            appendMessage(ChatMessage(text = webResponse, isFromUser = false))
        } catch (e: Exception) {
            appendMessage(ChatMessage(text = "سيدي، تفضل بأمرك وسأكون جاهزة دائماً لمساعدتك.", isFromUser = false))
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onAppChosen(app: AppResolver.InstalledApp, pendingAction: PendingAction) {
        val reply = intentHandler.executeChoice(app, pendingAction)
        appendMessage(ChatMessage(text = reply, isFromUser = false))
        _uiState.update { it.copy(pendingChooser = null) }
    }
}
