package com.racha.ai.ui.screens.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.core.agent.CommandResult
import com.racha.ai.core.agent.LocalAgent
import com.racha.ai.core.apps.AppChooserState
import com.racha.ai.core.apps.AppResolver
import com.racha.ai.core.apps.IntentHandler
import com.racha.ai.core.contacts.ContactResolver
import com.racha.ai.core.context.ContextBuilder
import com.racha.ai.core.context.SmartReplies
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.data.api.GeminiService
import com.racha.ai.data.llm.QwenService
import com.racha.ai.data.local.ChatMessageEntity
import com.racha.ai.data.repository.ChatMemoryRepository
import com.racha.ai.data.repository.UserProfileRepository
import com.racha.ai.data.search.KnowledgeAugmenter
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
    val qwenLoading: Boolean = false,
    val qwenModelReady: Boolean = false,
    val qwenCopying: Boolean = false,
    val isSearching: Boolean = false,
    val appChooser: AppChooserState = AppChooserState()
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val securePrefs = SecurePreferences(application)
    private val appPrefs = AppPreferences(application)
    private val memory = ChatMemoryRepository(application)
    private val profileRepo = UserProfileRepository(application)
    private val localAgent = LocalAgent(application)
    private val contextBuilder = ContextBuilder(application)

    private val appResolver = AppResolver(application)
    private val contactResolver = ContactResolver(application)
    private val intentHandler = IntentHandler(application, appResolver, contactResolver)

    private var geminiService: GeminiService? = null
    private var qwenService: QwenService? = null
    private var augmenter: KnowledgeAugmenter? = null

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

        qwenService = QwenService(getApplication())
        augmenter = KnowledgeAugmenter(getApplication(), qwenService!!)

        _uiState.value = _uiState.value.copy(
            activeLLM = appPrefs.getLLMProvider(),
            qwenModelReady = qwenService?.isModelPresent() ?: false
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

    fun copyQwenModel(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(qwenCopying = true)
            val result = qwenService?.copyModelFromUri(uri)
            result?.fold(
                onSuccess = {
                    saveMessageEntity(
                        Message(text = "✅ تم نسخ نموذج Qwen بنجاح.", isFromUser = false)
                    )
                    _uiState.value = _uiState.value.copy(qwenCopying = false, qwenModelReady = true)
                },
                onFailure = { error ->
                    saveMessageEntity(
                        Message(
                            text = "⚠️ فشل نسخ النموذج: ${error.message ?: "غير معروف"}",
                            isFromUser = false,
                            isError = true
                        )
                    )
                    _uiState.value = _uiState.value.copy(qwenCopying = false)
                }
            )
        }
    }

    fun onAppSelected(app: AppResolver.InstalledApp) {
        val chooser = _uiState.value.appChooser
        val action = chooser.pendingAction

        _uiState.value = _uiState.value.copy(appChooser = AppChooserState())

        if (action == null) return

        viewModelScope.launch {
            val result = intentHandler.executeChoice(app, action)
            saveMessageEntity(Message(text = result, isFromUser = false))
        }
    }

    fun onAppChooserDismiss() {
        _uiState.value = _uiState.value.copy(appChooser = AppChooserState())
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isSending) return

        viewModelScope.launch {
            val userMessage = Message(text = text, isFromUser = true)
            saveMessageEntity(userMessage)
            _uiState.value = _uiState.value.copy(inputText = "", isSending = true)

            // ===== 1. LocalAgent =====
            val localResult = localAgent.processCommand(text)
            when (localResult) {
                is CommandResult.Executed -> {
                    saveMessageEntity(Message(text = localResult.message, isFromUser = false))
                    _uiState.value = _uiState.value.copy(isSending = false)
                    return@launch
                }
                is CommandResult.NeedsAction -> {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        pendingSpecialTool = localResult.action
                    )
                    return@launch
                }
                is CommandResult.Failed -> {
                    saveMessageEntity(
                        Message(text = localResult.message, isFromUser = false, isError = true)
                    )
                    _uiState.value = _uiState.value.copy(isSending = false)
                    return@launch
                }
                is CommandResult.NeedsLLM -> { /* نكمل */ }
            }

            // ===== 2. IntentHandler =====
            val intentResult = intentHandler.handle(text)
            when (intentResult) {
                is IntentHandler.HandlerResult.DirectReply -> {
                    saveMessageEntity(Message(text = intentResult.message, isFromUser = false))
                    _uiState.value = _uiState.value.copy(isSending = false)
                    return@launch
                }
                is IntentHandler.HandlerResult.ShowChooser -> {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        appChooser = AppChooserState(
                            isVisible = true,
                            title = intentResult.title,
                            apps = intentResult.apps,
                            pendingAction = intentResult.pendingAction
                        )
                    )
                    return@launch
                }
                is IntentHandler.HandlerResult.Error -> {
                    saveMessageEntity(
                        Message(text = intentResult.message, isFromUser = false, isError = true)
                    )
                    _uiState.value = _uiState.value.copy(isSending = false)
                    return@launch
                }
                is IntentHandler.HandlerResult.Unknown -> { /* نكمل */ }
            }

            // ===== 3. بناء السياق =====
            val recent = memory.getRecentForContext()
            val history = recent.dropLast(1)
            val context = contextBuilder.build(history, text)

            // ===== 4. SmartReplies =====
            val smartReply = SmartReplies.tryReply(text, context)
            if (smartReply != null) {
                saveMessageEntity(Message(text = smartReply, isFromUser = false))
                _uiState.value = _uiState.value.copy(isSending = false)
                return@launch
            }

            // ===== 5. LLM =====
            val profileContext = profileRepo.getPromptContext()
            val contextPrompt = context.toPrompt()

            val provider = _uiState.value.activeLLM
            if (provider == "qwen" && augmenter != null) {
                sendWithAugmenter(text, history, contextPrompt, profileContext)
            } else {
                sendToGemini(text, history, contextPrompt, profileContext)
            }
        }
    }

    private suspend fun sendWithAugmenter(
        text: String,
        history: List<ChatMessageEntity>,
        contextPrompt: String,
        profileContext: String
    ) {
        val aug = augmenter ?: run {
            sendToGemini(text, history, contextPrompt, profileContext)
            return
        }

        if (qwenService?.isModelPresent() != true) {
            saveMessageEntity(
                Message(
                    text = "⚠️ نموذج Qwen غير موجود.",
                    isFromUser = false,
                    isError = true
                )
            )
            _uiState.value = _uiState.value.copy(isSending = false)
            return
        }

        if (qwenService?.isReady() != true) {
            _uiState.value = _uiState.value.copy(qwenLoading = true)
        }

        val fullContext = "$profileContext\n\n$contextPrompt"
        val result = aug.smartAnswer(text, fullContext, history)

        saveMessageEntity(
            Message(text = result.answer, isFromUser = false, isError = false)
        )

        _uiState.value = _uiState.value.copy(
            isSending = false,
            qwenLoading = false
        )
    }

    private suspend fun sendToGemini(
        text: String,
        history: List<ChatMessageEntity>,
        contextPrompt: String,
        profileContext: String
    ) {
        val service = geminiService
        if (service == null) {
            saveMessageEntity(
                Message(
                    text = "⚠️ مفتاح Gemini غير موجود.",
                    isFromUser = false,
                    isError = true
                )
            )
            _uiState.value = _uiState.value.copy(isSending = false)
            return
        }

        val fullContext = "$profileContext\n\n$contextPrompt"
        val result = service.sendMessageWithContext(history, text, fullContext)

        result.fold(
            onSuccess = { reply ->
                saveMessageEntity(Message(text = reply, isFromUser = false))
                _uiState.value = _uiState.value.copy(isSending = false)
            },
            onFailure = { error ->
                val errorMessage = error.message ?: ""
                val userFriendly = when {
                    errorMessage.contains("503") ||
                    errorMessage.contains("UNAVAILABLE") -> "⚠️ Gemini مزدحم حالياً."
                    errorMessage.contains("API key") -> "⚠️ المفتاح غير صالح."
                    errorMessage.contains("quota") -> "⚠️ استُهلكت الحصة."
                    else -> "⚠️ خطأ: ${error.message ?: "غير معروف"}"
                }
                saveMessageEntity(
                    Message(text = userFriendly, isFromUser = false, isError = true)
                )
                _uiState.value = _uiState.value.copy(isSending = false)
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
