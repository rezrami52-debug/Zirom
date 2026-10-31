package com.racha.ai.data.llm

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.racha.ai.data.local.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QwenService(private val context: Context) : LLMProvider {

    companion object {
        private const val TAG = "RachaQwen"
        private const val MODEL_FILENAME = "qwen2.5-1.5b-instruct.task"
        private val modelMutex = Mutex()
    }

    override val name: String = "Qwen (محلي)"

    private var llmInference: LlmInference? = null
    private var isInitialized = false

    fun getModelFile(): File {
        val primaryPath = File("/storage/emulated/0/Racha/models", MODEL_FILENAME)
        if (primaryPath.exists() && primaryPath.length() > 100_000_000) return primaryPath

        val secondaryPath = File("/sdcard/Racha/models", MODEL_FILENAME)
        if (secondaryPath.exists() && secondaryPath.length() > 100_000_000) return secondaryPath

        return File(File(context.filesDir, "models"), MODEL_FILENAME)
    }

    fun isModelPresent(): Boolean {
        val f = getModelFile()
        return f.exists() && f.length() > 100_000_000
    }

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        modelMutex.withLock {
            try {
                if (isInitialized && llmInference != null) return@withContext Result.success(Unit)

                val modelFile = getModelFile()
                if (!modelFile.exists() || modelFile.length() < 100_000_000) {
                    return@withContext Result.failure(Exception("ملف Qwen غير موجود"))
                }

                // ضبط معايير الاستدلال لمعالجات الهواتف
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(1024)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                isInitialized = true
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun isReady(): Boolean = isInitialized && llmInference != null

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val res = sendMessageWithContext(emptyList(), prompt, "")
        res.getOrElse { "تعذر استخراج الإجابة محلياً: ${it.localizedMessage}" }
    }

    override suspend fun sendMessageWithContext(
        history: List<ChatMessageEntity>,
        userMessage: String,
        userProfileContext: String
    ): Result<String> = withContext(Dispatchers.IO) {
        modelMutex.withLock {
            try {
                if (!isInitialized) {
                    val initResult = initialize()
                    initResult.onFailure { return@withContext Result.failure(it) }
                }

                val inference = llmInference ?: return@withContext Result.failure(Exception("النموذج غير جاهز"))
                val fullPrompt = buildPrompt(history, userMessage, userProfileContext)

                val response = inference.generateResponse(fullPrompt)
                Result.success(response.trim())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun getLivePhoneContext(): String {
        return try {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val bIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = bIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = bIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 75
            val charging = bIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
            "الوقت: $time | البطارية: $pct% ${if (charging) "(في الشاحن)" else ""} | الموقع: سوسة، تونس"
        } catch (_: Exception) {
            "الموقع: سوسة، تونس"
        }
    }

    private fun buildPrompt(
        history: List<ChatMessageEntity>,
        userMessage: String,
        userProfileContext: String
    ): String {
        val sb = StringBuilder()
        val phoneStatus = getLivePhoneContext()

        sb.append("<|im_start|>system\n")
        sb.append("أنت EVE و Racha، رفيق ذكي وخبير برمجيات وأنظمة لصديقك رمزي.\n")
        sb.append("معطيات الهاتف الحية: [$phoneStatus].\n\n")

        sb.append("قواعد السلوك:\n")
        sb.append("1. أجب بالعربية العفوية والطبيعية وتحدث معه باسمه 'رمزي' كصديق مقرب.\n")
        sb.append("2. إجاباتك ذكية ومباشرة (1 إلى 2 أسطر).\n")
        sb.append("3. افهم اللهجة التونسية بطلاقة (مثال: 'شنية أحوالك' = كيف حالك، 'حللي' = افتح لي، 'وقتاش' = متى).\n")
        sb.append("4. إذا طلب رمزي فتح تطبيق أو فيديو أو تشغيل فلاش، وافق بود وأضف وسماً برمجياً في نهاية ردك:\n")
        sb.append("   - لفتح تطبيق: [ACTION: OPEN_APP(اسم التطبيق بالإنجليزية مثل facebook, tiktok, youtube)]\n")
        sb.append("   - لفتح الفيديوهات: [ACTION: OPEN_VIDEOS]\n")
        sb.append("   - لفتح الصور: [ACTION: OPEN_PHOTOS]\n")
        sb.append("   - لتشغيل الفلاش: [ACTION: FLASHLIGHT(on)] ولإطفائه: [ACTION: FLASHLIGHT(off)]\n")
        sb.append("   - لقفل الشاشة: [ACTION: LOCK_SCREEN]\n")
        sb.append("<|im_end|>\n")

        for (msg in history.takeLast(4)) {
            val role = if (msg.isFromUser) "user" else "assistant"
            sb.append("<|im_start|>$role\n${msg.text}<|im_end|>\n")
        }

        sb.append("<|im_start|>user\n$userMessage<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    override fun close() {
        try {
            llmInference?.close()
            llmInference = null
            isInitialized = false
        } catch (_: Exception) {}
    }
}
