package com.racha.ai.ui.screens.chat

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.core.agent.SelfEvolutionAgent
import com.racha.ai.core.apps.AppResolver
import com.racha.ai.core.apps.IntentHandler
import com.racha.ai.core.apps.IntentClassifier
import com.racha.ai.core.apps.PendingAction
import com.racha.ai.core.command.RachaCommand
import com.racha.ai.core.command.RachaCommandRouter
import com.racha.ai.core.command.LearnedLexiconResolver
import com.racha.ai.core.contacts.ContactResolver
import com.racha.ai.core.context.LocationIntentHandler
import com.racha.ai.core.eve.EveAccessibilityService
import com.racha.ai.core.eve.EveBubbleService
import com.racha.ai.core.eve.EveState
import com.racha.ai.core.eve.MediaShelfType
import com.racha.ai.core.memory.MemoryLearningPolicy
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.core.tools.ApkSourceDecompiler
import com.racha.ai.core.tools.ApkSourceDecompiler.ExtractedFileEntry
import com.racha.ai.core.tools.LocalApkCommandMatcher
import com.racha.ai.data.api.GeminiService
import com.racha.ai.core.memory.SmartScreenProcessor
import com.racha.ai.data.llm.QwenService
import com.racha.ai.data.local.KnowledgeEntity
import com.racha.ai.data.local.ChatMessageEntity
import com.racha.ai.data.local.ConversationEntity
import com.racha.ai.data.local.RachaDatabase
import com.racha.ai.data.repository.KnowledgeRepository
import com.racha.ai.services.location.LocationHelper
import com.racha.ai.services.weather.WeatherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
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
    val selectedFileUri: String? = null,
    val selectedFileSummary: String? = null,
    val selectedFileName: String? = null,
    val isApkDecompiling: Boolean = false,
    val apkDecompileStatus: String? = null,
    val decompiledSourceZipPath: String? = null,
    val decompiledFiles: List<ExtractedFileEntry> = emptyList(),
    val isDecompiledBrowserOpen: Boolean = false,
    val decompiledSearchQuery: String = "",
    val selectedDecompiledFilePath: String? = null,
    val decompiledFilePreview: String? = null,
    val isLoadingDecompiledPreview: Boolean = false,
    val selectedImageUri: String? = null,
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
    private var fileAnalysisJob: Job? = null
    private var apkDecompilationJob: Job? = null
    private var sendJob: Job? = null
    private var lastLearnedScreenText: String? = null
    private var lastScreenAnalysisAt: Long = 0L
    @Volatile private var userLexiconEntries: List<KnowledgeEntity> = emptyList()
    @Volatile private var userLexiconLoaded = false
    private var userLexiconLoadJob: Job? = null

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
        fileAnalysisJob?.cancel()
        fileAnalysisJob = null
        apkDecompilationJob?.cancel()
        apkDecompilationJob = null
        _uiState.value.decompiledSourceZipPath?.let { File(it).delete() }
        val previousPendingImage = _uiState.value.pendingImagePath
        _uiState.update {
            it.copy(
                messages = emptyList(),
                inputText = "",
                isLoading = false,
                pendingChooser = null,
                pendingSpecialTool = null,
                pendingImagePath = null,
                selectedFileUri = null,
                selectedFileSummary = null,
                selectedFileName = null,
                isApkDecompiling = false,
                apkDecompileStatus = null,
                decompiledSourceZipPath = null,
                decompiledFiles = emptyList(),
                isDecompiledBrowserOpen = false,
                decompiledSearchQuery = "",
                selectedDecompiledFilePath = null,
                decompiledFilePreview = null,
                isLoadingDecompiledPreview = false,
                selectedImageUri = null
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
    fun onFileSelected(uri: android.net.Uri?) {
        if (uri == null) return
        fileAnalysisJob?.cancel()
        apkDecompilationJob?.cancel()
        apkDecompilationJob = null
        _uiState.value.decompiledSourceZipPath?.let { File(it).delete() }
        removePendingImage()
        val uriValue = uri.toString()
        val displayName = queryFileDisplayName(uri)
        _uiState.update {
            it.copy(
                selectedFileUri = uriValue,
                selectedFileSummary = FILE_ANALYSIS_LOADING,
                selectedFileName = displayName,
                isApkDecompiling = false,
                apkDecompileStatus = null,
                decompiledSourceZipPath = null,
                decompiledFiles = emptyList(),
                isDecompiledBrowserOpen = false,
                decompiledSearchQuery = "",
                selectedDecompiledFilePath = null,
                decompiledFilePreview = null,
                isLoadingDecompiledPreview = false,
                selectedImageUri = null
            )
        }

        fileAnalysisJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val analysis = withTimeout(90_000L) {
                    com.racha.ai.core.tools.FileAnalyzer.analyzeFile(getApplication(), uri)
                }
                _uiState.update {
                    if (it.selectedFileUri == uriValue) it.copy(selectedFileSummary = analysis.summary) else it
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    if (it.selectedFileUri == uriValue) {
                        it.copy(selectedFileSummary = "تعذّر تحليل الملف محليًا. أعد اختياره وحاول مجددًا؛ لم يُرفع الملف.")
                    } else it
                }
            }
        }
    }

    fun clearSelectedFile() {
        fileAnalysisJob?.cancel()
        fileAnalysisJob = null
        apkDecompilationJob?.cancel()
        apkDecompilationJob = null
        _uiState.value.decompiledSourceZipPath?.let { File(it).delete() }
        _uiState.update {
            it.copy(
                selectedFileUri = null,
                selectedFileSummary = null,
                selectedFileName = null,
                isApkDecompiling = false,
                apkDecompileStatus = null,
                decompiledSourceZipPath = null,
                decompiledFiles = emptyList(),
                isDecompiledBrowserOpen = false,
                decompiledSearchQuery = "",
                selectedDecompiledFilePath = null,
                decompiledFilePreview = null,
                isLoadingDecompiledPreview = false
            )
        }
    }

    private fun queryFileDisplayName(uri: Uri): String {
        val fromProvider = runCatching {
            getApplication<Application>().contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }.getOrNull()
        return (fromProvider ?: uri.lastPathSegment ?: "ملف مجهول").take(160)
    }

    fun decompileSelectedApk() {
        val current = _uiState.value
        val uriValue = current.selectedFileUri ?: return
        if (!current.selectedFileName.orEmpty().endsWith(".apk", ignoreCase = true) || current.isApkDecompiling) return

        current.decompiledSourceZipPath?.let { File(it).delete() }
        _uiState.update {
            it.copy(
                isApkDecompiling = true,
                apkDecompileStatus = "يجري تفكيك شيفرة الحزمة الرئيسية محليًا. قد يستغرق ذلك وقتًا.",
                decompiledSourceZipPath = null,
                decompiledFiles = emptyList(),
                isDecompiledBrowserOpen = false,
                decompiledSearchQuery = "",
                selectedDecompiledFilePath = null,
                decompiledFilePreview = null,
                isLoadingDecompiledPreview = false
            )
        }

        lateinit var decompilationJob: Job
        decompilationJob = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                val result = com.racha.ai.core.tools.ApkSourceDecompiler.decompileMainPackage(
                    context = getApplication(),
                    uri = Uri.parse(uriValue),
                    onProgress = { done, total ->
                        _uiState.update { state ->
                            if (state.selectedFileUri == uriValue && state.isApkDecompiling) {
                                state.copy(apkDecompileStatus = "تقدم JADX: $done من $total مهمة")
                            } else state
                        }
                    },
                    shouldCancel = {
                        !decompilationJob.isActive || _uiState.value.selectedFileUri != uriValue
                    }
                )
                _uiState.update { state ->
                    if (state.selectedFileUri == uriValue) {
                        state.copy(
                            isApkDecompiling = false,
                            decompiledSourceZipPath = result.archive.absolutePath,
                            decompiledFiles = result.files,
                            isDecompiledBrowserOpen = true,
                            decompiledSearchQuery = "",
                            apkDecompileStatus = buildString {
                                append("اكتمل استخراج ${result.files.size} ملف للحزمة ${result.packageName}، منها ${result.javaFileCount} ملف Java-like. ")
                                append("أخطاء JADX: ${result.jadxErrors}. حجم ZIP: ${result.archiveBytes / 1024} KB. ")
                                append("الناتج تقريبي وقد لا يطابق المصدر الأصلي؛ الشيفرة للحزمة الرئيسية فقط وقد تكون الموارد أوسع.")
                            }
                        )
                    } else {
                        result.archive.delete()
                        state
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _uiState.update { state ->
                    if (state.selectedFileUri == uriValue) {
                        state.copy(
                            isApkDecompiling = false,
                            apkDecompileStatus = "أُلغيت عملية التفكيك.",
                            decompiledFiles = emptyList(),
                            isDecompiledBrowserOpen = false,
                            selectedDecompiledFilePath = null,
                            decompiledFilePreview = null,
                            isLoadingDecompiledPreview = false
                        )
                    } else state
                }
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e("ChatViewModel", "JADX package decompilation failed", error)
                _uiState.update { state ->
                    if (state.selectedFileUri == uriValue) {
                        state.copy(
                            isApkDecompiling = false,
                            apkDecompileStatus = "تعذر تفكيك الحزمة: ${error.message?.take(180) ?: "خطأ غير متوقع"}. لم يُرفع APK."
                        )
                    } else state
                }
            }
        }
        apkDecompilationJob = decompilationJob
        decompilationJob.start()
    }

    fun cancelApkDecompilation() {
        apkDecompilationJob?.cancel()
    }

    fun exportDecompiledSources(destination: Uri) {
        val sourcePath = _uiState.value.decompiledSourceZipPath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val source = File(sourcePath)
                require(source.isFile) { "ملف ZIP المؤقت لم يعد متاحًا؛ أعد التفكيك." }
                val output = getApplication<Application>().contentResolver.openOutputStream(destination, "w")
                    ?: throw IllegalStateException("تعذر فتح ملف التصدير للكتابة.")
                source.inputStream().buffered().use { input ->
                    output.buffered().use { sink -> input.copyTo(sink) }
                }
                _uiState.update { state ->
                    state.copy(apkDecompileStatus = "تم تصدير ملفات Java-like محليًا. لم يُرسل APK إلى أي خدمة.")
                }
            } catch (error: Exception) {
                _uiState.update { state ->
                    state.copy(apkDecompileStatus = "تعذر تصدير الملفات: ${error.message?.take(160) ?: "خطأ غير متوقع"}.")
                }
            }
        }
    }

    fun openDecompiledBrowser() {
        if (_uiState.value.decompiledFiles.isNotEmpty()) {
            _uiState.update { it.copy(isDecompiledBrowserOpen = true) }
        }
    }

    fun closeDecompiledBrowser() {
        _uiState.update { it.copy(isDecompiledBrowserOpen = false) }
    }

    fun setDecompiledSearchQuery(query: String) {
        _uiState.update { it.copy(decompiledSearchQuery = query.take(200)) }
    }

    fun previewDecompiledFile(path: String) {
        val state = _uiState.value
        val archivePath = state.decompiledSourceZipPath ?: return
        val entry = state.decompiledFiles.firstOrNull { it.path == path } ?: return
        _uiState.update {
            it.copy(
                selectedDecompiledFilePath = path,
                decompiledFilePreview = if (entry.isTextPreviewable) null else "هذا ملف غير نصي؛ يمكنك تصديره لفتحه محليًا بتطبيق مناسب.",
                isLoadingDecompiledPreview = entry.isTextPreviewable
            )
        }
        if (!entry.isTextPreviewable) return

        viewModelScope.launch(Dispatchers.IO) {
            val preview = runCatching {
                ApkSourceDecompiler.readTextPreview(File(archivePath), path)
            }.getOrElse { error ->
                "تعذرت معاينة الملف محليًا: ${error.message?.take(160) ?: "خطأ غير متوقع"}"
            }
            _uiState.update {
                if (it.selectedDecompiledFilePath == path && it.decompiledSourceZipPath == archivePath) {
                    it.copy(decompiledFilePreview = preview, isLoadingDecompiledPreview = false)
                } else it
            }
        }
    }

    fun exportDecompiledFile(path: String, destination: Uri) {
        val archivePath = _uiState.value.decompiledSourceZipPath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val output = getApplication<Application>().contentResolver.openOutputStream(destination, "w")
                    ?: throw IllegalStateException("تعذر فتح ملف التصدير للكتابة.")
                output.use { ApkSourceDecompiler.copyArchiveEntry(File(archivePath), path, it) }
                _uiState.update { it.copy(apkDecompileStatus = "تم تصدير الملف المحدد محليًا؛ لم يُرسل محتواه إلى خدمة ذكاء اصطناعي.") }
            } catch (error: Exception) {
                _uiState.update { it.copy(apkDecompileStatus = "تعذر تصدير الملف: ${error.message?.take(160) ?: "خطأ غير متوقع"}.") }
            }
        }
    }

    fun clearSelectedImage() {
        _uiState.update { it.copy(selectedImageUri = null) }
    }


    fun onInputChange(newText: String) {
        _uiState.update { it.copy(inputText = newText) }
    }

    fun onImageSelected(uri: Uri?) {
        if (uri == null) return
        clearSelectedFile()
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
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "image/avif" -> "avif"
            "image/bmp" -> "bmp"
            "image/gif" -> "gif"
            else -> throw IllegalArgumentException(
                "تعذر تحديد صيغة الصورة. اختر صورة JPEG أو PNG أو WebP أو HEIC/HEIF."
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
        const val FILE_ANALYSIS_LOADING = "جارٍ تحليل الملف محليًا..."
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

    private fun handleChatMusicPlaybackCommand(text: String): Boolean {
        val playback = EveState.uiState.value.playback
        if (playback.title.isBlank()) return false

        val normalized = text.lowercase()
            .replace(Regex("[ًٌٍَُِّْـ]"), "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace(Regex("\\s+"), " ")
            .trim()
        val pauseVerbs = listOf("اوقفي", "اوقف", "توقفي", "توقف", "وقف", "اغلقي", "اغلق")
        val isPauseCommand = normalized.contains("pause") ||
            (normalized.contains("مؤقت") && pauseVerbs.any { normalized.contains(it) })
        val stopVerbs = listOf("اوقفي", "اوقف", "توقفي", "توقف", "وقف", "اغلقي", "اغلق", "اقفلي", "اقفل", "اسكتي", "اقطعي", "stop")
        val action = when {
            isPauseCommand -> EveBubbleService.ACTION_PAUSE_MUSIC
            stopVerbs.any { normalized.contains(it) } -> EveBubbleService.ACTION_STOP_MUSIC
            else -> return false
        }

        return try {
            val app = getApplication<Application>()
            app.startService(Intent(app, EveBubbleService::class.java).setAction(action))
            appendMessage(
                ChatMessage(
                    text = if (action == EveBubbleService.ACTION_PAUSE_MUSIC) {
                        "أوقفت الموسيقى مؤقتًا يا رمزي."
                    } else {
                        "أوقفت الموسيقى وأزلت مؤشرها من فقاعة إيف يا رمزي."
                    },
                    isFromUser = false
                )
            )
            true
        } catch (error: Exception) {
            android.util.Log.e("ChatViewModel", "تعذر إرسال أمر التحكم بالموسيقى إلى إيف", error)
            appendMessage(
                ChatMessage(
                    text = "تعذر إيقاف الموسيقى من المحادثة الآن. جرّب التحكم من فقاعة إيف.",
                    isFromUser = false
                )
            )
            true
        }
    }

    private suspend fun refreshUserLexicon() {
        userLexiconEntries = knowledgeRepo.getUserLexiconEntries()
        userLexiconLoaded = true
    }

    private fun isCurrentWeatherQuestion(text: String): Boolean {
        val normalized = text.lowercase()
            .replace(Regex("[ًٌٍَُِّْـ]"), "")
            .replace(Regex("[أإآٱ]"), "ا")
            .replace('ى', 'ي')
        return listOf("طقس", "الجو", "درجة الحرارة", "حرارة اليوم", "weather", "forecast")
            .any { normalized.contains(it) }
    }

    private fun isWeatherRetry(text: String, previousMessages: List<ChatMessage>): Boolean {
        val normalized = text.lowercase()
            .replace(Regex("[ًٌٍَُِّْـ]"), "")
            .replace(Regex("[أإآٱ]"), "ا")
            .replace('ى', 'ي')
        val retry = listOf("جرب", "حاول", "اعد", "مرة ثانية", "مرة اخرى", "من جديد", "try again", "retry")
            .any { normalized.contains(it) }
        return retry && previousMessages.takeLast(12).any {
            it.isFromUser && isCurrentWeatherQuestion(LearnedLexiconResolver.expand(it.text, userLexiconEntries))
        }
    }

    private fun fetchWeatherForChat() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val location = runCatching { LocationHelper.getCurrentLocation(app) }.getOrNull()
                val latitude = location?.latitude ?: 35.8256
                val longitude = location?.longitude ?: 10.6369
                val city = location?.cityName?.takeIf { it.isNotBlank() } ?: "سوسة"
                val weather = WeatherService.fetchWeather(latitude, longitude, city)
                val locationNote = if (location == null) "لم أتمكن من قراءة موقع الهاتف، فاستخدمت إحداثيات سوسة كافتراض. " else ""
                val reply = if (weather == null) {
                    "تعذر جلب تحديث الطقس الآن. قد تكون البطاقة تعرض آخر قراءة ناجحة؛ تحقق من الإنترنت ثم قل «جرّب الآن» لإعادة المحاولة."
                } else {
                    "$locationNote الطقس الحالي في ${weather.cityName.ifBlank { city }}: ${weather.description()}، الحرارة ${weather.temperature.toInt()}°م، المحسوسة ${weather.feelsLike.toInt()}°م، الرطوبة ${weather.humidity}%، والرياح ${weather.windSpeed.toInt()} كم/س."
                }
                appendMessage(ChatMessage(text = reply, isFromUser = false))
            } catch (error: Exception) {
                android.util.Log.e("ChatViewModel", "تعذر جلب الطقس للمحادثة", error)
                appendMessage(ChatMessage(text = "تعذر تحديث الطقس الآن. تحقق من الإنترنت ثم قل «جرّب الآن» لإعادة المحاولة.", isFromUser = false))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun sendMessage() {
        val currentState = _uiState.value
        // Do not allow duplicate AI/file requests from repeated taps.
        if (currentState.isLoading) return
        if (!userLexiconLoaded) {
            if (userLexiconLoadJob?.isActive != true) {
                val expectedInput = currentState.inputText
                userLexiconLoadJob = viewModelScope.launch(Dispatchers.IO) {
                    refreshUserLexicon()
                    withContext(Dispatchers.Main) {
                        if (_uiState.value.inputText == expectedInput) sendMessage()
                    }
                }
            }
            return
        }
        val userText = currentState.inputText
        val trimmed = userText.trim()
        val imagePath = currentState.pendingImagePath

        if (LocalApkCommandMatcher.matches(trimmed)) {
            if (trimmed.isNotBlank()) appendMessage(ChatMessage(text = trimmed, isFromUser = true))
            _uiState.update { it.copy(inputText = "") }
            when {
                currentState.selectedFileUri == null -> appendMessage(
                    ChatMessage(text = "أرفق ملف APK أولًا؛ سأفككه محليًا ولن أرسل هذا الأمر إلى Gemini أو Qwen.", isFromUser = false)
                )
                !currentState.selectedFileName.orEmpty().endsWith(".apk", ignoreCase = true) -> appendMessage(
                    ChatMessage(text = "الأمر المحلي الحالي مخصص لملفات APK. أرفق APK موثوقًا ثم أعد الطلب.", isFromUser = false)
                )
                currentState.decompiledFiles.isNotEmpty() -> openDecompiledBrowser()
                else -> decompileSelectedApk()
            }
            return
        }

        // APK/ZIP والملفات العامة تُعرض محليًا فقط؛ لا تدخل في طلب Gemini/Qwen ولا تُحفظ في Room.
        if (currentState.selectedFileUri != null) {
            val localReport = currentState.selectedFileSummary
            if (localReport.isNullOrBlank() || localReport == FILE_ANALYSIS_LOADING) return
            val localMessages = buildList {
                if (trimmed.isNotBlank()) add(ChatMessage(text = trimmed, isFromUser = true))
                add(ChatMessage(text = localReport, isFromUser = false))
            }
            fileAnalysisJob?.cancel()
            apkDecompilationJob?.cancel()
            apkDecompilationJob = null
            currentState.decompiledSourceZipPath?.let { File(it).delete() }
            _uiState.update {
                it.copy(
                    inputText = "",
                    selectedFileUri = null,
                    selectedFileSummary = null,
                    selectedFileName = null,
                    isApkDecompiling = false,
                    apkDecompileStatus = null,
                    decompiledSourceZipPath = null,
                    decompiledFiles = emptyList(),
                    isDecompiledBrowserOpen = false,
                    decompiledSearchQuery = "",
                    selectedDecompiledFilePath = null,
                    decompiledFilePreview = null,
                    isLoadingDecompiledPreview = false,
                    selectedImageUri = null,
                    messages = it.messages + localMessages
                )
            }
            return
        }
        
        // دمج سياق الملف والصورة المرفقة في النص المرسل للذكاء الاصطناعي
        val fileContext = if (currentState.selectedFileSummary != null) {
            "\n\n[📎 ملف مرفق للتحليل:\n${currentState.selectedFileSummary}]"
        } else ""
        
        val imageContext = if (currentState.selectedImageUri != null) {
            "\n\n[🖼️ صورة مرفقة للتحليل: يرجى وصف محتواها أو الإجابة بناءً عليها.]"
        } else ""
        
        val finalQuery = "$trimmed$fileContext$imageContext"

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
            _uiState.update { it.copy(inputText = "", pendingImagePath = null, selectedFileUri = null, selectedFileSummary = null, selectedImageUri = null) }
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

        _uiState.update { it.copy(inputText = "", selectedFileUri = null, selectedFileSummary = null, selectedImageUri = null) }
        if (trimmed.isBlank()) return

        appendMessage(
            ChatMessage(
                text = trimmed,
                isFromUser = true
            )
        )

        val routedText = LearnedLexiconResolver.expand(trimmed, userLexiconEntries)
        val routedFinalQuery = if (routedText != trimmed) {
            "$routedText\n[الصياغة الأصلية للمستخدم: $trimmed]$fileContext$imageContext"
        } else finalQuery

        if (handleChatMusicPlaybackCommand(routedText)) return

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

                val saved = knowledgeRepo.quickSaveVerified(
                    question = pendingTerm,
                    answer = cleanMeaning,
                    topic = LearnedLexiconResolver.LEXICON_TOPIC,
                    keywords = "$pendingTerm,$cleanMeaning",
                    source = "user"
                )
                if (saved) refreshUserLexicon()

                val originalQuestion = originalPendingQuestion
                pendingLearningTerm = null
                originalPendingQuestion = null

                appendMessage(
                    ChatMessage(
                        text = if (saved) {
                            "✅ حفظت معنى «$pendingTerm» واستخدمه الآن عند تفسير الطلبات التي تحتويه."
                        } else {
                            "تعذر حفظ المعنى محليًا هذه المرة، لذلك لن أدّعي أنني سأطبقه لاحقًا."
                        },
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
                val saved = knowledgeRepo.quickSaveVerified(
                    question = term,
                    answer = meaning,
                    topic = LearnedLexiconResolver.LEXICON_TOPIC,
                    keywords = "$term,$meaning",
                    source = "user"
                )
                if (saved) refreshUserLexicon()

                appendMessage(
                    ChatMessage(
                        text = if (saved) {
                            "✅ حفظت «$term» بمعنى «$meaning»، وسأستخدم هذا المعنى لفهم طلباتك التالية."
                        } else {
                            "تعذر حفظ هذا التعريف محليًا؛ لم أتمكن من تفعيله للاستعمال لاحقًا."
                        },
                        isFromUser = false
                    )
                )
            }
            return
        }

        if (isCurrentWeatherQuestion(routedText) || isWeatherRetry(routedText, currentState.messages)) {
            fetchWeatherForChat()
            return
        }

        val centralCommand = RachaCommandRouter.classify(routedText)

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
        val normalizedMediaCommand = routedText
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

        val normalizedMusicRequest = routedText.lowercase()
            .replace(Regex("[ًٌٍَُِّْـ]"), "")
            .replace(Regex("[أإآٱ]"), "ا")
        val opensMusic = listOf("موسيقى", "موسيقي", "اغاني", "اغنية", "صوتيات", "music", "song")
            .any { normalizedMusicRequest.contains(it) } &&
            listOf("افتح", "افتحي", "شغل", "شغلي", "تشغيل", "اعرض", "اعرضي", "play", "open", "show")
                .any { normalizedMusicRequest.contains(it) }
        if (opensMusic) {
            EveState.openShelf(MediaShelfType.MUSIC)
            appendMessage(ChatMessage(text = "فتحت قائمة الموسيقى المحلية يا رمزي؛ اختر أغنية لبدء تشغيلها.", isFromUser = false))
            return
        }

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

        val conversationalIntent = IntentClassifier.classify(routedText)
        if (conversationalIntent == IntentClassifier.IntentType.GREETING ||
            conversationalIntent == IntentClassifier.IntentType.SMALL_TALK ||
            conversationalIntent == IntentClassifier.IntentType.CORRECTION
        ) {
            handleAiQuery(routedFinalQuery)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val result = withContext(Dispatchers.IO) {
                    intentHandler.handle(routedText)
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
                        if (LearnedLexiconResolver.looksLikeAction(routedText)) {
                            appendMessage(
                                ChatMessage(
                                    text = "فهمت طلبك بعد تطبيق المعنى الذي علمتني إياه، لكن لا توجد في هذا الإصدار وظيفة محلية لتنفيذه. لم أنفذ أي إجراء.",
                                    isFromUser = false
                                )
                            )
                            _uiState.update { it.copy(isLoading = false) }
                        } else {
                            handleAiQuery(routedFinalQuery)
                        }
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
            val selectedProvider = appPrefs.getLLMProvider()
            // isReady() is false on first use even when the model file exists.
            // Initialize once before selecting the local provider.
            val qwenReady = if (selectedProvider == "qwen" && qwenService.isModelPresent()) {
                qwenService.initialize().isSuccess
            } else {
                false
            }
            val enrichedQuery = ConversationContextBuilder.build(
                messages = _uiState.value.messages,
                currentQuestion = query,
                learnedContext = learnedContext,
                maxChars = if (selectedProvider == "qwen" && qwenReady) 1_500 else ConversationContextBuilder.DEFAULT_MAX_CHARS
            )

            if (
                selectedProvider == "qwen" &&
                qwenReady
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
                            .takeLast(1500) // أبقِ السؤال الحالي عند اقتطاع السياق للنموذج المحلي.
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
        fileAnalysisJob?.cancel()
        apkDecompilationJob?.cancel()
        sendJob?.cancel()
        // QwenService is process-shared; close() intentionally does not destroy
        // the native model while EVE or another consumer may still use it.
        qwenService.close()
        super.onCleared()
    }
}
