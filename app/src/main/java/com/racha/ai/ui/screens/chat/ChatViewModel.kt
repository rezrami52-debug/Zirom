package com.racha.ai.ui.screens.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import com.racha.ai.core.eve.EveState
import com.racha.ai.core.eve.MediaShelfType
import com.racha.ai.core.memory.MemoryLearningPolicy
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.data.api.GeminiService
import com.racha.ai.core.memory.SmartScreenProcessor
import com.racha.ai.data.llm.QwenService
import com.racha.ai.data.local.ChatMessageEntity
import com.racha.ai.data.local.ConversationEntity
import com.racha.ai.data.local.RachaDatabase
import com.racha.ai.data.repository.KnowledgeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val imagePath: String? = null
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val hasApiKey: Boolean = false,
    val pendingSpecialTool: String? = null,
    val pendingChooser: IntentHandler.HandlerResult.ShowChooser? = null,
    val isScreenLearningActive: Boolean = false,
    val pendingImagePath: String? = null
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

    private val rachaDatabase = RachaDatabase.getDatabase(application)
    private val conversationDao = rachaDatabase.conversationDao()
    private val chatMessageDao = rachaDatabase.chatMessageDao()

    private var activeConversationId: String? = null

    private var pendingLearningTerm: String? = null
    private var originalPendingQuestion: String? = null

    private var screenLearningJob: Job? = null
    private var lastLearnedScreenText: String? = null
    private var lastScreenAnalysisAt: Long = 0L

    init {
        _uiState.update {
            it.copy(
                messages = emptyList(),
                hasApiKey = !securePrefs.getApiKey().isNullOrBlank()
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            restoreLatestConversation()
        }
    }

    fun startNewChat() {
        val previousPendingImage = _uiState.value.pendingImagePath
        _uiState.update {
            it.copy(
                messages = emptyList(),
                inputText = "",
                isLoading = false,
                pendingChooser = null,
                pendingSpecialTool = null,
                pendingImagePath = null
            )
        }

        pendingLearningTerm = null
        originalPendingQuestion = null

        viewModelScope.launch(Dispatchers.IO) {
            deletePrivateChatImage(previousPendingImage)
            val conversation = ConversationEntity(
                id = UUID.randomUUID().toString(),
                title = "محادثة جديدة",
                timestamp = System.currentTimeMillis()
            )

            conversationDao.insertConversation(conversation)
            activeConversationId = conversation.id
        }
    }

    fun onInputChange(newText: String) {
        _uiState.update { it.copy(inputText = newText) }
    }

    fun onImageSelected(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            try {
                val imageFile = withContext(Dispatchers.IO) { persistSelectedImage(uri) }
                val previousPath = _uiState.value.pendingImagePath
                _uiState.update { it.copy(pendingImagePath = imageFile.absolutePath) }
                deletePrivateChatImage(previousPath)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to import selected image", e)
                appendMessage(
                    ChatMessage(
                        text = e.message ?: "تعذر قراءة الصورة. اختر صورة JPEG أو PNG أو WebP لا يتجاوز حجمها 12 ميغابايت.",
                        isFromUser = false
                    )
                )
            }
        }
    }

    fun removePendingImage() {
        val previousPath = _uiState.value.pendingImagePath ?: return
        _uiState.update { it.copy(pendingImagePath = null) }
        viewModelScope.launch(Dispatchers.IO) { deletePrivateChatImage(previousPath) }
    }

    private fun persistSelectedImage(uri: Uri): File {
        val app = getApplication<Application>()
        val mimeType = app.contentResolver.getType(uri)?.substringBefore(';')?.lowercase()
        val extension = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> throw IllegalArgumentException(
                "نوع الصورة غير مدعوم. اختر JPEG أو PNG أو WebP."
            )
        }

        val imageDirectory = File(app.filesDir, "chat_images")
        if (!imageDirectory.exists() && !imageDirectory.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد الصور داخل التطبيق.")
        }

        val fileId = UUID.randomUUID().toString()
        val temporaryFile = File(imageDirectory, "$fileId.part")
        val destinationFile = File(imageDirectory, "$fileId.$extension")
        try {
            val input = app.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("تعذر فتح الصورة المختارة.")
            input.use { source ->
                FileOutputStream(temporaryFile).buffered().use { sink ->
                    val buffer = ByteArray(16 * 1024)
                    var totalBytes = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        if (totalBytes > MAX_CHAT_IMAGE_BYTES) {
                            throw IllegalArgumentException("حجم الصورة يتجاوز الحد المسموح (12 ميغابايت).")
                        }
                        sink.write(buffer, 0, count)
                    }
                    if (totalBytes == 0L) {
                        throw IllegalArgumentException("ملف الصورة فارغ.")
                    }
                }
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporaryFile.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IllegalArgumentException("الملف المحدد ليس صورة قابلة للقراءة.")
            }
            if (!temporaryFile.renameTo(destinationFile)) {
                throw IllegalStateException("تعذر حفظ الصورة داخل التطبيق.")
            }
            return destinationFile
        } catch (e: Exception) {
            temporaryFile.delete()
            destinationFile.delete()
            throw e
        }
    }

    private fun deletePrivateChatImage(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val app = getApplication<Application>()
            val directory = File(app.filesDir, "chat_images").canonicalFile
            val target = File(path).canonicalFile
            if (target.parentFile == directory) target.delete()
        } catch (e: Exception) {
            android.util.Log.w("ChatViewModel", "Could not delete temporary image", e)
        }
    }

    private fun decodeImageForAnalysis(path: String): Bitmap {
        val file = File(path)
        require(file.isFile) { "ملف الصورة المحفوظة غير موجود." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "تعذر فك ترميز الصورة." }

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_SIDE) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IllegalArgumentException("تعذر تجهيز الصورة للتحليل.")
    }

    private fun isCredentialInput(text: String): Boolean {
        val value = text.trim()
        return value.startsWith("ghp_") || value.startsWith("github:", ignoreCase = true) ||
            value.startsWith("github_pat_") || value.startsWith("gsk_") ||
            value.startsWith("AIza") || value.startsWith("Bearer ", ignoreCase = true) ||
            value.startsWith("مفتاح:") || value.startsWith("api:", ignoreCase = true)
    }

    private fun analyzeAttachedImage(imagePath: String, userPrompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            var bitmap: Bitmap? = null
            try {
                val apiKey = securePrefs.getApiKey()
                if (apiKey.isNullOrBlank() || apiKey.startsWith("gsk_")) {
                    appendMessage(
                        ChatMessage(
                            text = "تحليل الصور يحتاج مفتاح Gemini محفوظ في الإعدادات؛ لم يتم إرسال الصورة إلى أي خدمة. أضف مفتاح Gemini ثم أعد المحاولة.",
                            isFromUser = false
                        )
                    )
                    return@launch
                }

                val decodedBitmap = decodeImageForAnalysis(imagePath)
                bitmap = decodedBitmap
                val response = withTimeout(60_000L) {
                    GeminiService(apiKey).analyzeImage(decodedBitmap, userPrompt)
                }
                appendMessage(ChatMessage(text = response, isFromUser = false))
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Image analysis failed", e)
                appendMessage(
                    ChatMessage(
                        text = "تعذر تحليل الصورة الآن. تحقق من اتصال الإنترنت وصلاحية مفتاح Gemini، ثم أعد اختيار الصورة.",
                        isFromUser = false
                    )
                )
            } finally {
                bitmap?.recycle()
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private companion object {
        const val MAX_CHAT_IMAGE_BYTES = 12L * 1024L * 1024L
        const val MAX_IMAGE_SIDE = 1536
        const val DEFAULT_IMAGE_PROMPT = "صف الصورة المرفقة واشرح أهم ما يظهر فيها."
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

        if (message.text.isBlank() && message.imagePath.isNullOrBlank()) return
        if (message.text.isNotBlank() && !isPersistableChatMessage(message.text)) return

        viewModelScope.launch(Dispatchers.IO) {
            val conversationId = ensureActiveConversation()

            chatMessageDao.insertMessage(
                ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    text = message.text,
                    isFromUser = message.isFromUser,
                    isError = false,
                    timestamp = System.currentTimeMillis(),
                    imagePath = message.imagePath
                )
            )

            if (message.isFromUser) {
                conversationDao.updateConversationTitle(
                    id = conversationId,
                    title = message.text
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .take(45)
                )
            }
        }
    }


    private suspend fun ensureActiveConversation(): String {
        activeConversationId?.let { return it }

        val latest = conversationDao
            .getAllConversations()
            .first()
            .firstOrNull()

        if (latest != null) {
            activeConversationId = latest.id
            return latest.id
        }

        val created = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = "محادثة جديدة",
            timestamp = System.currentTimeMillis()
        )

        conversationDao.insertConversation(created)
        activeConversationId = created.id
        return created.id
    }

    private suspend fun restoreLatestConversation() {
        val latest = conversationDao
            .getAllConversations()
            .first()
            .firstOrNull()

        val conversation = latest ?: ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = "محادثة جديدة",
            timestamp = System.currentTimeMillis()
        ).also {
            conversationDao.insertConversation(it)
        }

        activeConversationId = conversation.id

        val savedMessages = chatMessageDao
            .getMessagesByConversation(conversation.id)
            .first()

        val restoredMessages = savedMessages.map {
            ChatMessage(
                text = it.text,
                isFromUser = it.isFromUser,
                imagePath = it.imagePath
            )
        }

        _uiState.update {
            it.copy(messages = restoredMessages)
        }
    }

    private fun isPersistableChatMessage(text: String): Boolean {
        val value = text.trim()

        if (value.isBlank()) return false

        return !value.startsWith("ghp_") &&
            !value.startsWith("gsk_") &&
            !value.startsWith("AIza") &&
            !value.startsWith("github_pat_") &&
            !value.startsWith("Bearer ") &&
            !value.startsWith("مفتاح:") &&
            !value.startsWith("api:")
    }

    fun sendMessage() {
        val currentState = _uiState.value
        val userText = currentState.inputText
        val trimmed = userText.trim()
        val imagePath = currentState.pendingImagePath

        if (imagePath != null) {
            if (isCredentialInput(trimmed)) {
                appendMessage(
                    ChatMessage(
                        text = "أزل الصورة أولاً قبل إدخال مفتاح API، حتى لا يُرسل المفتاح مع الصورة.",
                        isFromUser = false
                    )
                )
                return
            }

            val imagePrompt = trimmed.ifBlank { DEFAULT_IMAGE_PROMPT }
            _uiState.update { it.copy(inputText = "", pendingImagePath = null) }
            appendMessage(
                ChatMessage(
                    text = imagePrompt,
                    isFromUser = true,
                    imagePath = imagePath
                )
            )
            analyzeAttachedImage(imagePath, imagePrompt)
            return
        }

        _uiState.update { it.copy(inputText = "") }
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


        if (isClearScreenMemoryCommand(trimmed)) {
            clearScreenLearningMemories()
            return
        }

        if (isShowLearnedMemoryCommand(trimmed)) {
            showLearnedScreenMemory()
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

        // تنفيذ أوامر الوسائط مباشرة حتى لا تذهب إلى نموذج الذكاء الاصطناعي
        // أو تتوقف عند IntentHandler إذا اختلفت صياغة الأمر.
        val normalizedMediaCommand = trimmed
            .lowercase()
            .replace(Regex("[ًٌٍَُِّْـ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val isTvCommand =
            normalizedMediaCommand.contains("تلفاز") ||
                normalizedMediaCommand.contains("تلفزيون") ||
                normalizedMediaCommand.contains("تلفزة") ||
                normalizedMediaCommand.contains("قنوات") ||
                normalizedMediaCommand.contains("iptv") ||
                normalizedMediaCommand.contains("بث مباشر")

        if (centralCommand is RachaCommand.OpenPhotos) {
            EveState.openShelf(MediaShelfType.PHOTOS)
            appendMessage(
                ChatMessage(
                    text = "🖼️ تفضل يا رمزي، تم فتح ألبوم الصور في نافذة عائمة.",
                    isFromUser = false
                )
            )
            return
        }

        if (centralCommand is RachaCommand.OpenVideos) {
            EveState.openShelf(MediaShelfType.VIDEOS)
            appendMessage(
                ChatMessage(
                    text = "📽️ تفضل يا رمزي، تم فتح مقاطع الفيديو في نافذة عائمة.",
                    isFromUser = false
                )
            )
            return
        }

        if (isTvCommand) {
            EveState.openShelf(MediaShelfType.LIVE_TV)
            appendMessage(
                ChatMessage(
                    text = "📺 تفضل يا رمزي، تم فتح التلفاز والبث المباشر في نافذة عائمة.",
                    isFromUser = false
                )
            )
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
            _uiState.update { it.copy(isScreenLearningActive = false) }

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

        _uiState.update { it.copy(isScreenLearningActive = true) }
        screenLearningJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(2500L)

                val snapshot = EveAccessibilityService.instance
                    ?.getLatestScreenSnapshot()
                    ?: continue

                val safeText = SmartScreenProcessor.cleanAndFormat(
                    snapshot.visibleText
                )

                if (!SmartScreenProcessor.isWorthAnalyzing(safeText)) {
                    continue
                }

                if (SmartScreenProcessor.containsSensitiveData(safeText)) {
                    continue
                }

                if (safeText == lastLearnedScreenText) {
                    continue
                }

                val now = System.currentTimeMillis()

                if (now - lastScreenAnalysisAt < 8_000L) {
                    continue
                }

                lastLearnedScreenText = safeText
                lastScreenAnalysisAt = now

                val screenPrompt = """
                    حلّل محتوى الشاشة التالي بهدف التعلم طويل المدى.

                    التطبيق: ${snapshot.appName}
                    الحزمة: ${snapshot.packageName}

                    المحتوى:
                    $safeText

                    أعد النتيجة بهذه الصيغة فقط:
                    مفيد: نعم أو لا
                    العنوان: عنوان قصير للمعلومة
                    المعلومة: شرح عربي واضح ومختصر
                    الموضوع: تصنيف المعلومة
                    الكلمات: كلمات مفتاحية مفصولة بفواصل

                    القواعد:
                    - أجب بمفيد: لا إذا كان المحتوى مجرد أزرار أو إشعار عابر أو قائمة مؤقتة.
                    - لا تحفظ كلمات المرور أو مفاتيح API أو الرموز السرية أو البيانات الشخصية.
                    - تجاهل كلمات مثل رجوع، إلغاء، مشاركة، فتح، إغلاق.
                    - لا تنقل النص حرفيًا؛ افهمه وأعد صياغته بالعربية.
                    - إذا لم توجد معلومة قابلة للتعلم، أعد مفيد: لا فقط.
                """.trimIndent()

                val memory = try {
                    val analysis = withTimeout(25_000L) {
                        qwenService.generateResponse(screenPrompt)
                    }

                    SmartScreenProcessor.parseScreenMemory(analysis)
                } catch (e: Exception) {
                    android.util.Log.e(
                        "ChatViewModel",
                        "فشل تحليل الشاشة للتعلم",
                        e
                    )
                    null
                }

                if (memory != null) {
                    val memoryKey = "screen:${snapshot.packageName}:${memory.title}"
                        .lowercase()
                        .replace(Regex("\\s+"), "_")
                        .take(220)

                    val structuredAnswer = buildString {
                        append("العنوان: ")
                        append(memory.title)
                        append("\nالتطبيق: ")
                        append(snapshot.appName)
                        append("\nالموضوع: ")
                        append(memory.topic)
                        append("\nالمعلومة: ")
                        append(memory.information)
                    }

                    knowledgeRepo.saveOrUpdate(
                        question = memoryKey,
                        answer = structuredAnswer,
                        topic = memory.topic,
                        keywords = listOf(
                            snapshot.appName,
                            snapshot.packageName,
                            memory.keywords
                        ).joinToString(","),
                        source = "screen_learning"
                    )
                }
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
                qwenService.isReady()
            ) {
                try {
                    val localReply = withTimeout(60_000L) {
                        qwenService.generateResponse(
                        enrichedQuery
                            .replace("<|im_start|>", "[بداية]")
                            .replace("<|im_end|>", "[نهاية]")
                            .take(1500) // حد أقصى آمن لمنع انهيار MediaPipe
                    )
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

            if (qwenService.isReady()) {
                try {
                    val fallbackReply = withTimeout(60_000L) {
                        qwenService.generateResponse(
                        enrichedQuery
                            .replace("<|im_start|>", "[بداية]")
                            .replace("<|im_end|>", "[نهاية]")
                            .take(1500) // حد أقصى آمن لمنع انهيار MediaPipe
                    )
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


    private fun isShowLearnedMemoryCommand(text: String): Boolean {
        val normalized = text
            .trim()
            .lowercase()
            .replace(Regex("[ًٌٍَُِّْـ]"), "")
            .replace(Regex("\\s+"), " ")

        return normalized == "اعرضي ما تعلمت" ||
            normalized == "اعرضي ماذا تعلمت" ||
            normalized == "ماذا تعلمت" ||
            normalized == "ما تعلمت" ||
            normalized == "اعرضي ما تعلمتي"
    }


    private fun isClearScreenMemoryCommand(text: String): Boolean {
        val normalized = text
            .trim()
            .lowercase()
            .replace(Regex("[ًٌٍَُِّْـ]"), "")
            .replace(Regex("\\s+"), " ")

        return normalized == "امسحي ذاكرة الشاشة القديمة" ||
            normalized == "امسحي ذاكرة الشاشة" ||
            normalized == "احذفي ذاكرة الشاشة القديمة" ||
            normalized == "احذفي ذكريات الشاشة"
    }

    private fun clearScreenLearningMemories() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                knowledgeRepo.deleteScreenLearningMemories()

                appendMessage(
                    ChatMessage(
                        text = "تم حذف ذكريات الشاشة القديمة فقط. المحادثات وبقية الذاكرة لم تتأثر.",
                        isFromUser = false
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e(
                    "ChatViewModel",
                    "فشل حذف ذكريات الشاشة",
                    e
                )

                appendMessage(
                    ChatMessage(
                        text = "تعذر حذف ذكريات الشاشة حاليًا.",
                        isFromUser = false
                    )
                )
            }
        }
    }

    private fun showLearnedScreenMemory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val memories = knowledgeRepo
                    .getAll()
                    .first()
                    .filter { memory ->
                        memory.source == "screen_learning"
                    }
                    .take(10)

                if (memories.isEmpty()) {
                    appendMessage(
                        ChatMessage(
                            text = "لم أحفظ أي معلومات مفهومة من متابعة الشاشة حتى الآن.",
                            isFromUser = false
                        )
                    )
                    return@launch
                }

                val builder = StringBuilder()
                builder.append("🧠 المعلومات التي فهمتها من الشاشة:\n\n")

                memories.forEachIndexed { index, memory ->
                    builder.append("${index + 1}. ")
                    builder.append(memory.answer.take(700))
                    builder.append("\n\n")
                }

                builder.append("المجموع المعروض: ${memories.size}")

                appendMessage(
                    ChatMessage(
                        text = builder.toString().trim(),
                        isFromUser = false
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e(
                    "ChatViewModel",
                    "فشل عرض ذاكرة الشاشة",
                    e
                )

                appendMessage(
                    ChatMessage(
                        text = "تعذر قراءة الذاكرة المحلية الآن.",
                        isFromUser = false
                    )
                )
            }
        }
    }

    override fun onCleared() {
        screenLearningJob?.cancel()
        screenLearningJob = null
        super.onCleared()
    }
}
