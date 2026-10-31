package com.racha.ai.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.core.agent.SelfEvolutionAgent
import com.racha.ai.core.apps.AppResolver
import com.racha.ai.core.apps.IntentHandler
import com.racha.ai.core.apps.PendingAction
import com.racha.ai.core.contacts.ContactResolver
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.data.api.GeminiService
import com.racha.ai.data.llm.QwenService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val appPrefs = AppPreferences(application)
    private val appResolver = AppResolver(application)
    private val contactResolver = ContactResolver(application)
    private val intentHandler = IntentHandler(application, appResolver, contactResolver)
    private val qwenService = QwenService(application)
    private val selfEvolutionAgent = SelfEvolutionAgent(application)

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

    fun sendMessage() {
        val userText = _uiState.value.inputText
        _uiState.update { it.copy(inputText = "") }

        val trimmed = userText.trim()
        if (trimmed.isBlank()) return

        appendMessage(ChatMessage(text = trimmed, isFromUser = true))

        if (trimmed.startsWith("ghp_") || trimmed.startsWith("github:")) {
            val token = trimmed.replace(Regex("(?i)github:"), "").trim()
            securePrefs.saveGitHubToken(token)
            appendMessage(ChatMessage(text = "✅ تم ربط مفتاح GitHub بنجاح يا رمزي! أنا الآن قادرة على قراءة وتطوير الكود بنفسي.", isFromUser = false))
            return
        }

        if (trimmed.startsWith("gsk_") || trimmed.startsWith("AIza") || trimmed.startsWith("مفتاح:") || trimmed.startsWith("api:")) {
            val extractedKey = trimmed.replace(Regex("(?i)مفتاح:|api:"), "").trim()
            securePrefs.saveApiKey(extractedKey)
            _uiState.update { it.copy(hasApiKey = true) }
            val provider = if (extractedKey.startsWith("gsk_")) "Groq (Llama 3.3 ⚡)" else "Gemini"
            appendMessage(ChatMessage(text = "✅ تم تفعيل وحفظ مفتاح $provider بنجاح يا رمزي!", isFromUser = false))
            return
        }

        val lower = trimmed.lowercase()
        if ((lower.contains("افحصي كود") || lower.contains("راجعي كود") || lower.contains("طوري كود")) && (lower.contains("البوصلة") || lower.contains("الطقس") || lower.contains(".kt"))) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                val targetFile = if (lower.contains("البوصلة") || lower.contains("الطقس")) {
                    "app/src/main/java/com/racha/ai/ui/screens/chat/WeatherBannerWithPrayerTimes.kt"
                } else {
                    trimmed.substringAfter("كود").trim().split(" ").first()
                }

                val reply = selfEvolutionAgent.auditAndProposeFix(targetFile, trimmed)
                appendMessage(ChatMessage(text = reply, isFromUser = false))
                _uiState.update { it.copy(isLoading = false) }
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val result = withContext(Dispatchers.IO) { intentHandler.handle(trimmed) }
                when (result) {
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
                        appendMessage(ChatMessage(text = "هل يمكنك توضيح: '${result.text}' يا رمزي؟", isFromUser = false))
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    is IntentHandler.HandlerResult.Unknown -> {
                        handleAiQuery(trimmed)
                    }
                }
            } catch (e: Exception) {
                appendMessage(ChatMessage(text = "عذراً يا رمزي، واجهت مشكلة في معالجة طلبك.", isFromUser = false))
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun handleAiQuery(query: String) = withContext(Dispatchers.IO) {
        val selectedProvider = appPrefs.getLLMProvider()

        if (selectedProvider == "qwen" && qwenService.isModelPresent()) {
            try {
                val localReply = qwenService.generateResponse(query)
                // تنفيذ الأوامر المرفقة من كوين وحذف الوسم من الشاشة
                val cleanText = executeActionsIfPresent(localReply)
                appendMessage(ChatMessage(text = "$cleanText \n\n📱 [Qwen محلي أوفلاين]", isFromUser = false))
                _uiState.update { it.copy(isLoading = false) }
                return@withContext
            } catch (_: Exception) {}
        }

        val apiKey = securePrefs.getApiKey()
        if (!apiKey.isNullOrBlank()) {
            try {
                val geminiService = GeminiService(apiKey)
                val response = geminiService.generateResponse(query)
                appendMessage(ChatMessage(text = response, isFromUser = false))
                _uiState.update { it.copy(isLoading = false) }
                return@withContext
            } catch (_: Exception) {}
        }

        if (qwenService.isModelPresent()) {
            try {
                val fallbackReply = qwenService.generateResponse(query)
                val cleanText = executeActionsIfPresent(fallbackReply)
                appendMessage(ChatMessage(text = "$cleanText \n\n📱 [Qwen وضع أوفلاين التلقائي]", isFromUser = false))
                _uiState.update { it.copy(isLoading = false) }
                return@withContext
            } catch (_: Exception) {}
        }

        appendMessage(ChatMessage(
            text = "يا رمزي، يمكنك إما استخدام نموذج Qwen المحلي من الإعدادات، أو لصق مفتاح Groq المجاني (gsk_...) هنا مباشرة!",
            isFromUser = false
        ))
        _uiState.update { it.copy(isLoading = false) }
    }

    /**
     * قراءة وسوم الأوامر الصادرة من Qwen وتنفيذها فوراً في الهاتف
     */
    private suspend fun executeActionsIfPresent(rawText: String): String {
        val actionRegex = Regex("\\[ACTION:\\s*([^\\]]+)\\]")
        val match = actionRegex.find(rawText)

        if (match != null) {
            val actionBody = match.groupValues[1].trim()
            val commandToRun = when {
                actionBody.startsWith("OPEN_APP") -> {
                    val app = actionBody.substringAfter("(").substringBefore(")").trim()
                    "افتحي $app في نافذة صغيرة"
                }
                actionBody == "OPEN_VIDEOS" -> "اعرضي مقاطع الفيديو"
                actionBody == "OPEN_PHOTOS" -> "اعرضي ألبوم الصور"
                actionBody.contains("FLASHLIGHT(on)") -> "شغلي الفلاش"
                actionBody.contains("FLASHLIGHT(off)") -> "طفي الفلاش"
                actionBody == "LOCK_SCREEN" -> "اقفلي الشاشة"
                else -> null
            }

            if (commandToRun != null) {
                try {
                    intentHandler.handle(commandToRun)
                } catch (_: Exception) {}
            }
        }

        return rawText.replace(actionRegex, "").trim()
    }

    fun onAppChosen(app: AppResolver.InstalledApp, pendingAction: PendingAction) {
        val reply = intentHandler.executeChoice(app, pendingAction)
        appendMessage(ChatMessage(text = reply, isFromUser = false))
        _uiState.update { it.copy(pendingChooser = null) }
    }
}
