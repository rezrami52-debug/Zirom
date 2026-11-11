package com.racha.ai.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.core.agent.SelfEvolutionAgent
import com.racha.ai.core.apps.AppResolver
import com.racha.ai.core.apps.IntentHandler
import com.racha.ai.core.apps.PendingAction
import com.racha.ai.core.command.RachaCommand
import com.racha.ai.core.command.RachaCommandRouter
import com.racha.ai.core.contacts.ContactResolver
import com.racha.ai.core.context.LocationIntentHandler
import com.racha.ai.core.eve.EveAccessibilityService
import com.racha.ai.core.memory.MemoryLearningPolicy
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.data.api.GeminiService
import com.racha.ai.data.llm.QwenService
import com.racha.ai.data.repository.KnowledgeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
    private val knowledgeRepo = KnowledgeRepository(application)

    private var pendingLearningTerm: String? = null
    private var originalPendingQuestion: String? = null

    private var screenLearningJob: Job? = null
    private var lastLearnedScreenText: String? = null

    init {
        _uiState.update {
            it.copy(
                messages = emptyList(),
                hasApiKey = !securePrefs.getApiKey().isNullOrBlank()
            )
        }
    }

    fun startNewChat() {
        _uiState.update {
            it.copy(
                messages = emptyList(),
                inputText = ""
            )
        }

        pendingLearningTerm = null
        originalPendingQuestion = null
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
        _uiState.update {
            it.copy(messages = it.messages + message)
        }
    }

    fun sendMessage() {
        val userText = _uiState.value.inputText
        _uiState.update { it.copy(inputText = "") }

        val trimmed = userText.trim()
        if (trimmed.isBlank()) return

        appendMessage(
            ChatMessage(
                text = trimmed,
                isFromUser = true
            )
        )

        if (MemoryLearningPolicy.isLearningTrigger(trimmed)) {
            toggleScreenLearningMode()
            return
        }

        if (trimmed.startsWith("ghp_") || trimmed.startsWith("github:")) {
            val token = trimmed
                .replace(Regex("(?i)github:"), "")
                .trim()

            securePrefs.saveGitHubToken(token)

            appendMessage(
                ChatMessage(
                    text = "✅ تم ربط مفتاح GitHub بنجاح يا رمزي!",
                    isFromUser = false
                )
            )
            return
        }

        if (
            trimmed.startsWith("gsk_") ||
            trimmed.startsWith("AIza") ||
            trimmed.startsWith("مفتاح:") ||
            trimmed.startsWith("api:")
        ) {
            val extractedKey = GeminiService.sanitizeApiKey(
                trimmed.replace(
                    Regex("(?i)مفتاح:|api:"),
                    ""
                )
            )

            securePrefs.saveApiKey(extractedKey)
            _uiState.update { it.copy(hasApiKey = true) }

            val provider = if (extractedKey.startsWith("gsk_")) {
                "Groq (Llama 3.3 ⚡)"
            } else {
                "Gemini"
            }

            appendMessage(
                ChatMessage(
                    text = "✅ تم تفعيل وحفظ مفتاح $provider بنجاح يا رمزي!",
                    isFromUser = false
                )
            )
            return
        }

        val pendingTerm = pendingLearningTerm

        if (pendingTerm != null) {
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(isLoading = true) }

                val cleanMeaning = trimmed
                    .replace(
                        Regex("(?i)نعني|نقصد|أقصد|اقصد|يعني|تعني|هو|هي|بأن"),
                        ""
                    )
                    .trim()

                knowledgeRepo.quickSave(
                    question = pendingTerm,
                    answer = cleanMeaning,
                    topic = "معجم رمزي",
                    keywords = "$pendingTerm,$cleanMeaning",
                    source = "user"
                )

                val originalQuestion = originalPendingQuestion
                pendingLearningTerm = null
                originalPendingQuestion = null

                appendMessage(
                    ChatMessage(
                        text = "✅ فهمتك يا رمزي! حفظت أن «$pendingTerm» تعني «$cleanMeaning».",
                        isFromUser = false
                    )
                )

                if (!originalQuestion.isNullOrBlank()) {
                    handleAiQuery(originalQuestion)
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            return
        }

        val directLearnMatch = matchDirectLearning(trimmed)

        if (directLearnMatch != null) {
            val (term, meaning) = directLearnMatch

            viewModelScope.launch(Dispatchers.IO) {
                knowledgeRepo.quickSave(
                    question = term,
                    answer = meaning,
                    topic = "معجم رمزي",
                    keywords = "$term,$meaning",
                    source = "user"
                )

                appendMessage(
                    ChatMessage(
                        text = "✅ سجلتها في ذاكرتي: «$term» تعني «$meaning».",
                        isFromUser = false
                    )
                )
            }
            return
        }

        val centralCommand = RachaCommandRouter.classify(trimmed)

        if (centralCommand is RachaCommand.GetLocation) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val reply = LocationIntentHandler.answer(getApplication())

                    appendMessage(
                        ChatMessage(
                            text = reply,
                            isFromUser = false
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e(
                        "ChatViewModel",
                        "فشل الحصول على الموقع",
                        e
                    )

                    appendMessage(
                        ChatMessage(
                            text = "عذرًا يا رمزي، تعذر قراءة موقعك الآن.",
                            isFromUser = false
                        )
                    )
                } finally {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            return
        }

        if (centralCommand is RachaCommand.AskAI) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }

                try {
                    handleAiQuery(trimmed)
                } catch (e: Exception) {
                    android.util.Log.e(
                        "ChatViewModel",
                        "فشل الموجّه المركزي",
                        e
                    )

                    appendMessage(
                        ChatMessage(
                            text = "عذراً يا رمزي، تعذر معالجة طلبك حالياً.",
                            isFromUser = false
                        )
                    )

                    _uiState.update { it.copy(isLoading = false) }
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val result = withContext(Dispatchers.IO) {
                    intentHandler.handle(trimmed)
                }

                when (result) {
                    is IntentHandler.HandlerResult.DirectReply -> {
                        appendMessage(
                            ChatMessage(
                                text = result.message,
                                isFromUser = false
                            )
                        )
                        _uiState.update { it.copy(isLoading = false) }
                    }

                    is IntentHandler.HandlerResult.ShowChooser -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                pendingChooser = result
                            )
                        }
                    }

                    is IntentHandler.HandlerResult.Error -> {
                        appendMessage(
                            ChatMessage(
                                text = result.message,
                                isFromUser = false
                            )
                        )
                        _uiState.update { it.copy(isLoading = false) }
                    }

                    is IntentHandler.HandlerResult.NeedsCorrection -> {
                        appendMessage(
                            ChatMessage(
                                text = "هل يمكنك توضيح: '${result.text}' يا رمزي؟",
                                isFromUser = false
                            )
                        )
                        _uiState.update { it.copy(isLoading = false) }
                    }

                    is IntentHandler.HandlerResult.Unknown -> {
                        handleAiQuery(trimmed)
                    }
                }
            } catch (e: Exception) {
                appendMessage(
                    ChatMessage(
                        text = "عذراً يا رمزي، واجهت مشكلة في معالجة طلبك.",
                        isFromUser = false
                    )
                )
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun toggleScreenLearningMode() {
        if (screenLearningJob?.isActive == true) {
            screenLearningJob?.cancel()
            screenLearningJob = null
            lastLearnedScreenText = null

            appendMessage(
                ChatMessage(
                    text = "⏸️ أوقفت وقت التعلم.",
                    isFromUser = false
                )
            )
            return
        }

        appendMessage(
            ChatMessage(
                text = "🧠 بدأ وقت التعلم. سأتابع الشاشة وأحفظ المعلومات الجديدة محليًا، ولن أحفظ الأسرار أو مفاتيح API.",
                isFromUser = false
            )
        )

        screenLearningJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(2500L)

                val snapshot = EveAccessibilityService.instance
                    ?.getLatestScreenSnapshot()
                    ?: continue

                val safeText = MemoryLearningPolicy.cleanForMemory(
                    snapshot.visibleText
                )

                if (safeText.isBlank()) continue
                if (safeText == lastLearnedScreenText) continue

                lastLearnedScreenText = safeText

                knowledgeRepo.saveOrUpdate(
                    question = "محتوى شاشة ${snapshot.appName} ${safeText.hashCode()}",
                    answer = safeText,
                    topic = "تعلم من الشاشة",
                    keywords = "${snapshot.appName},${snapshot.packageName}",
                    source = "screen_learning"
                )
            }
        }
    }

    private suspend fun handleAiQuery(query: String) =
        withContext(Dispatchers.IO) {
            val learnedContext = knowledgeRepo.buildContextFor(
                query,
                limit = 2
            )

            val enrichedQuery =
                if (learnedContext.isNotBlank()) {
                    "$learnedContext\nسؤال رمزي: $query"
                } else {
                    query
                }

            val selectedProvider = appPrefs.getLLMProvider()

            if (
                selectedProvider == "qwen" &&
                qwenService.isModelPresent()
            ) {
                try {
                    val localReply = withTimeout(60_000L) {
                        qwenService.generateResponse(enrichedQuery)
                    }

                    val cleanText = executeActionsIfPresent(localReply)

                    if (isUnclearResponse(cleanText)) {
                        val ambiguousWord = extractUnclearWord(query)
                        pendingLearningTerm = ambiguousWord
                        originalPendingQuestion = query

                        appendMessage(
                            ChatMessage(
                                text = "شنوة تقصد بـ '$ambiguousWord' يا رمزي؟ وضحلي معناها وسأحفظها عندي فوراً.",
                                isFromUser = false
                            )
                        )
                    } else {
                        appendMessage(
                            ChatMessage(
                                text = "$cleanText\n\n📱 [Qwen محلي أوفلاين]",
                                isFromUser = false
                            )
                        )
                    }

                    _uiState.update { it.copy(isLoading = false) }
                    return@withContext
                } catch (e: Exception) {
                    android.util.Log.e(
                        "ChatViewModel",
                        "فشل تنفيذ Qwen",
                        e
                    )
                }
            }

            val apiKey = securePrefs.getApiKey()

            if (!apiKey.isNullOrBlank()) {
                try {
                    val response = withTimeout(30_000L) {
                        GeminiService(apiKey).generateResponse(enrichedQuery)
                    }

                    appendMessage(
                        ChatMessage(
                            text = response,
                            isFromUser = false
                        )
                    )

                    _uiState.update { it.copy(isLoading = false) }
                    return@withContext
                } catch (e: Exception) {
                    android.util.Log.e(
                        "ChatViewModel",
                        "فشل المزود السحابي",
                        e
                    )
                }
            }

            if (qwenService.isModelPresent()) {
                try {
                    val fallbackReply = withTimeout(60_000L) {
                        qwenService.generateResponse(enrichedQuery)
                    }

                    val cleanText = executeActionsIfPresent(fallbackReply)

                    appendMessage(
                        ChatMessage(
                            text = "$cleanText\n\n📱 [Qwen وضع أوفلاين]",
                            isFromUser = false
                        )
                    )

                    _uiState.update { it.copy(isLoading = false) }
                    return@withContext
                } catch (e: Exception) {
                    android.util.Log.e(
                        "ChatViewModel",
                        "فشل النموذج الاحتياطي",
                        e
                    )
                }
            }

            appendMessage(
                ChatMessage(
                    text = "يا رمزي، فعّل Qwen المحلي أو أضف مفتاح Groq أو Gemini.",
                    isFromUser = false
                )
            )

            _uiState.update { it.copy(isLoading = false) }
        }

    private fun matchDirectLearning(text: String): Pair<String, String>? {
        val patterns = listOf(
            Regex("(?i)^(.+?)\\s+(?:تعني|يعني)\\s+(.+)$"),
            Regex("(?i)^(.+?)\\s+(?:هو|هي)\\s+(?:عبارة عن|بمعنى|معناها)\\s+(.+)$"),
            Regex("(?i)^احفظي\\s+(?:أن|ان|إن|إنو|انو)?\\s*(.+?)\\s+(?:تعني|يعني|هو|هي)\\s+(.+)$")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)

            if (match != null && match.groupValues.size >= 3) {
                val term = match.groupValues[1].trim()
                val meaning = match.groupValues[2].trim()

                if (term.length in 2..35 && meaning.isNotBlank()) {
                    return Pair(term, meaning)
                }
            }
        }

        return null
    }

    private fun isUnclearResponse(reply: String): Boolean {
        val triggers = listOf(
            "لا أعلم",
            "لا أعرف",
            "لست متأكداً",
            "لم أفهم",
            "غير واضح"
        )

        return triggers.any { reply.contains(it) }
    }

    private fun extractUnclearWord(query: String): String {
        val words = query
            .split(" ")
            .filter {
                it.length > 2 &&
                    !it.contains("كيف") &&
                    !it.contains("وين") &&
                    !it.contains("شنوة")
            }

        return words.lastOrNull() ?: query
    }

    private suspend fun executeActionsIfPresent(rawText: String): String {
        val actionRegex = Regex("\\[ACTION:\\s*([^\\]]+)\\]")
        val match = actionRegex.find(rawText)

        if (match != null) {
            val actionBody = match.groupValues[1].trim()

            val commandToRun = when {
                actionBody.startsWith("OPEN_APP") -> {
                    val app = actionBody
                        .substringAfter("(")
                        .substringBefore(")")
                        .trim()

                    "افتحي $app في نافذة صغيرة"
                }

                actionBody == "OPEN_VIDEOS" -> "اعرضي مقاطع الفيديو"
                actionBody == "OPEN_PHOTOS" -> "اعرضي ألبوم الصور"

                actionBody.contains("FLASHLIGHT(on)") ->
                    "شغلي الفلاش"

                actionBody.contains("FLASHLIGHT(off)") ->
                    "طفي الفلاش"

                actionBody == "LOCK_SCREEN" ->
                    "اقفلي الشاشة"

                actionBody == "FIND_PAVED_ROAD" ->
                    "أخرجيني إلى أقرب طريق معبد"

                actionBody.startsWith("NAVIGATE") -> {
                    val destination = Regex(
                        """destination\s*=\s*["']?([^)"']+)["']?"""
                    )
                        .find(actionBody)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.trim()
                        ?: actionBody
                            .substringAfter("(")
                            .substringBefore(")")
                            .trim()

                    if (destination.isNotBlank()) {
                        "خذيني إلى $destination"
                    } else {
                        null
                    }
                }

                else -> null
            }

            if (commandToRun != null) {
                try {
                    intentHandler.handle(commandToRun)
                } catch (e: Exception) {
                    android.util.Log.e(
                        "ChatViewModel",
                        "فشل تنفيذ الإجراء",
                        e
                    )
                }
            }
        }

        return rawText.replace(actionRegex, "").trim()
    }

    fun onAppChosen(
        app: AppResolver.InstalledApp,
        pendingAction: PendingAction
    ) {
        val reply = intentHandler.executeChoice(app, pendingAction)

        appendMessage(
            ChatMessage(
                text = reply,
                isFromUser = false
            )
        )

        _uiState.update {
            it.copy(pendingChooser = null)
        }
    }

    override fun onCleared() {
        screenLearningJob?.cancel()
        screenLearningJob = null
        super.onCleared()
    }
}
