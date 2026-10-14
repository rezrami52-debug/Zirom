package com.racha.ai.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.core.agent.CommandResult
import com.racha.ai.core.agent.LocalAgent
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.data.api.GeminiService
import com.racha.ai.data.llm.LLMProvider
import com.racha.ai.data.llm.QwenService
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
    val hasProfile: Boolean = false,
    val pendingSpecialTool: String? = null,
    val activeLLM: String = "gemini",
    val qwenLoading: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val securePrefs = SecurePreferences(application)
    private val appPrefs = AppPreferences(application)
    private val memory = ChatMemoryRepository(application)
    private val profileRepo = UserProfileRepository(application)
    private val localAgent = LocalAgent(application)

    private var geminiService: GeminiService? = null
    private var qwenService: QwenService? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var welcomeInitialized = false

    init {
        initializeServices()
        observeHistory()
        checkProfile()
    }

    private fun initializeServices() {
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

        qwenService = QwenService(application)
        _uiState.value = _uiState.value.copy(
            activeLLM = appPrefs.getLLMProvider()
        )
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
                        saveMessageEntity(welcome)
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

    fun switchLLM(provider: String) {
        appPrefs.setLLMProvider(provider)
        _uiState.value = _uiState.value.copy(activeLLM = provider)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isSending) return

        viewModelScope.launch {
            val userMessage = Message(text = text, isFromUser = true)
            saveMessageEntity(userMessage)
            _uiState.value = _uiState.value.copy(inputText = "", isSending = true)

            // 1. LocalAgent أولاً (فوري)
            val localResult = localAgent.processCommand(text)

            when (localResult) {
                is CommandResult.Executed -> {
                    saveMessageEntity(Message(text = localResult.message, isFromUser = false))
                    _uiState.value = _uiState.value.copy(isSending = false)
                }

                is CommandResult.NeedsAction -> {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        pendingSpecialTool = localResult.action
                    )
                }

                is CommandResult.Failed -> {
                    saveMessageEntity(
                        Message(text = localResult.message, isFromUser = false, isError = true)
                    )
                    _uiState.value = _uiState.value.copy(isSending = false)
                }

                is CommandResult.NeedsLLM -> {
                    // 2. اختر LLM المناسب
                    val provider = _uiState.value.activeLLM
                    if (provider == "qwen") {
                        sendToQwen(text)
                    } else {
                        sendToGemini(text)
                    }
                }
            }
        }
    }

    private suspend fun sendToGemini(text: String) {
        val service = geminiService
        if (service == null) {
            saveMessageEntity(
                Message(text = "⚠️ مفتاح Gemini غير موجود. أضفه من الإعدادات.", isFromUser = false, isError = true)
            )
            _uiState.value = _uiState.value.copy(isSending = false)
            return
        }

        val recent = memory.getRecentForContext()
        val history = recent.dropLast(1)
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
                saveMessageEntity(
                    Message(text = userFriendly, isFromUser = false, isError = true)
                )
                _uiState.value = _uiState.value.copy(isSending = false)
            }
        )
    }

    private suspend fun sendToQwen(text: String) {
        val service = qwenService
        if (service == null) {
            saveMessageEntity(
                Message(text = "⚠️ Qwen غير متاح.", isFromUser = false, isError = true)
            )
            _uiState.value = _uiState.value.copy(isSending = false)
            return
        }

        // إظهار "جاري التحميل" أول مرة
        if (!service.isReady()) {
            _uiState.value = _uiState.value.copy(qwenLoading = true)
        }

        val recent = memory.getRecentForContext()
        val history = recent.dropLast(1)
        val profileContext = profileRepo.getPromptContext()

        val result = service.sendMessageWithContext(history, text, profileContext)

        result.fold(
            onSuccess = { reply ->
                saveMessageEntity(Message(text = reply, isFromUser = false))
                _uiState.value = _uiState.value.copy(isSending = false, qwenLoading = false)
            },
            onFailure = { error ->
                val errorMessage = error.message ?: "خطأ غير معروف"
                val userFriendly = if (errorMessage.contains("غير موجود")) {
                    "⚠️ نموذج Qwen غير موجود.\n\nتأكد من نسخ النموذج إلى:\n/storage/emulated/0/Racha/models/"
                } else {
                    "⚠️ Qwen: $errorMessage"
                }
                saveMessageEntity(
                    Message(text = userFriendly, isFromUser = false, isError = true)
                )
                _uiState.value = _uiState.value.copy(isSending = false, qwenLoading = false)
            }
        )
    }

    fun onSpecialToolCompleted(success: Boolean, message: String) {
        viewModelScope.launch {
            saveMessageEntity(
                Message(text = message, isFromUser = false, isError = !success)
            )
        }
        _uiState.value = _uiState.value.copy(pendingSpecialTool = null)
    }

    fun onSpecialToolCancelled() {
        viewModelScope.launch {
            saveMessageEntity(
                Message(text = "❌ تم إلغاء العملية", isFromUser = false)
            )
        }
        _uiState.value = _uiState.value.copy(pendingSpecialTool = null)
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

    fun refreshProfileStatus() {
        checkProfile()
    }

    override fun onCleared() {
        super.onCleared()
        qwenService?.close()
    }
}
