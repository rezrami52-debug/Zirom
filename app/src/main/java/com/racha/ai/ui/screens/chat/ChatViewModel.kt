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
import com.racha.ai.core.tools.SystemController
import com.racha.ai.data.api.GeminiService
import com.racha.ai.data.llm.QwenService
import com.racha.ai.data.local.ChatMessageEntity
import com.racha.ai.data.local.ConversationEntity
import com.racha.ai.data.repository.ChatMemoryRepository
import com.racha.ai.data.repository.UserProfileRepository
import com.racha.ai.data.search.KnowledgeAugmenter
import com.racha.ai.domain.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

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
    val appChooser: AppChooserState = AppChooserState(),
    val currentConversationId: String = UUID.randomUUID().toString(), // ✅ معرف المحادثة الحالية
    val conversations: List<ConversationEntity> = emptyList() // ✅ قائمة المحادثات
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
    private val systemController = SystemController(application)
    private val intentHandler = IntentHandler(application, appResolver, contactResolver, systemController)

    private var geminiService: GeminiService? = null
    private var qwenService: QwenService? = null
    private var augmenter: KnowledgeAugmenter? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        initializeServices()
        observeConversations()
        startNewChat() // ✅ يبدأ بمحادثة جديدة دائماً عند فتح التطبيق
    }

    private fun initializeServices() {
        val apiKey = securePrefs.getApiKey()
        if (apiKey.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(hasApiKey = false, isLoadingHistory = false)
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

    private fun observeConversations() {
        viewModelScope.launch {
            memory.getAllConversations().collectLatest { convs ->
                _uiState.value = _uiState.value.copy(conversations = convs)
            }
        }
    }

    fun startNewChat() {
        viewModelScope.launch {
            val newId = memory.createNewConversation()
            _uiState.value = _uiState.value.copy(
                currentConversationId = newId,
                messages = emptyList(),
                inputText = "",
                isSending = false
            )
            // رسالة ترحيبية اختيارية
            saveMessageEntity(Message(text = "أهلاً بك! أنا Racha، كيف يمكنني مساعدتك اليوم؟", isFromUser = false))
        }
    }

    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(currentConversationId = conversationId, isLoadingHistory = true)
            memory.getMessagesByConversation(conversationId).collectLatest { entities ->
                val messages = entities.map { entity ->
                    Message(id = entity.id, text = entity.text, isFromUser = entity.isFromUser, timestamp = entity.timestamp, isError = entity.isError)
                }
                _uiState.value = _uiState.value.copy(messages = messages, isLoadingHistory = false)
            }
        }
    }

    fun onInputChange(newText: String) {
        _uiState.value = _uiState.value.copy(inputText = newText)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isSending) return

        viewModelScope.launch {
            val userMessage = Message(text = text, isFromUser = true)
            saveMessageEntity(userMessage)
            _uiState.value = _uiState.value.copy(inputText = "", isSending = true)

            val localResult = localAgent.processCommand(text)
            when (localResult) {
                is CommandResult.Executed -> {
                    saveMessageEntity(Message(text = localResult.message, isFromUser = false))
                    _uiState.value = _uiState.value.copy(isSending = false)
                    return@launch
                }
                is CommandResult.NeedsAction -> {
                    _uiState.value = _uiState.value.copy(isSending = false, pendingSpecialTool = localResult.action)
                    return@launch
                }
                is CommandResult.Failed -> {
                    saveMessageEntity(Message(text = localResult.message, isFromUser = false, isError = true))
                    _uiState.value = _uiState.value.copy(isSending = false)
                    return@launch
                }
                is CommandResult.NeedsLLM -> { /* نكمل */ }
            }

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
                        appChooser = AppChooserState(isVisible = true, title = intentResult.title, apps = intentResult.apps, pendingAction = intentResult.pendingAction)
                    )
                    return@launch
                }
                is IntentHandler.HandlerResult.Error -> {
                    saveMessageEntity(Message(text = intentResult.message, isFromUser = false, isError = true))
                    _uiState.value = _uiState.value.copy(isSending = false)
                    return@launch
                }
                is IntentHandler.HandlerResult.NeedsCorrection, is IntentHandler.HandlerResult.Unknown -> { /* نكمل للذكاء الاصطناعي */ }
            }

            // هنا يتم استدعاء الذكاء الاصطناعي (Gemini/Qwen) بشكل مبسط للتركيز على ميزة المحادثات
            saveMessageEntity(Message(text = "جاري المعالجة...", isFromUser = false))
            _uiState.value = _uiState.value.copy(isSending = false)
        }
    }

    private suspend fun saveMessageEntity(message: Message) {
        memory.saveMessage(
            _uiState.value.currentConversationId,
            ChatMessageEntity(
                id = message.id,
                text = message.text,
                isFromUser = message.isFromUser,
                isError = message.isError,
                timestamp = message.timestamp
            )
        )
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
}
