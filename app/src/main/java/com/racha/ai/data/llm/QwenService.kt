package com.racha.ai.data.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.racha.ai.data.local.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class QwenService(private val context: Context) : LLMProvider {

    companion object {
        private const val TAG = "RachaQwen"
        private const val MODEL_FILENAME = "qwen2.5-1.5b-instruct.task"
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
        try {
            if (isInitialized && llmInference != null) return@withContext Result.success(Unit)

            val modelFile = getModelFile()
            if (!modelFile.exists() || modelFile.length() < 100_000_000) {
                return@withContext Result.failure(Exception("ملف Qwen غير موجود"))
            }

            Log.d(TAG, "تحميل Qwen من: ${modelFile.absolutePath}")
            val startTime = System.currentTimeMillis()

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isInitialized = true

            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            Log.d(TAG, "✅ تم تجهيز Qwen بنجاح في ${elapsed}ثانية")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "فشل تهيئة Qwen", e)
            Result.failure(e)
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
        try {
            if (!isInitialized) {
                val initResult = initialize()
                initResult.onFailure { return@withContext Result.failure(it) }
            }

            val inference = llmInference ?: return@withContext Result.failure(Exception("النموذج غير جاهز"))
            val fullPrompt = buildPrompt(history, userMessage, userProfileContext)

            val startTime = System.currentTimeMillis()
            val response = inference.generateResponse(fullPrompt)
            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            Log.d(TAG, "✅ تم التوليد محلياً في ${elapsed}ثانية")

            Result.success(response.trim())
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في التوليد", e)
            Result.failure(e)
        }
    }

    private fun buildPrompt(
        history: List<ChatMessageEntity>,
        userMessage: String,
        userProfileContext: String
    ): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n")
        sb.append("أنت EVE و Racha، رفيق شخصي ومساعد ذكي لصديقك رمزي.\n")
        sb.append("قواعد صارمة:\n")
        sb.append("1. أجب بالعربية العفوية والطبيعية.\n")
        sb.append("2. كن مقتضباً ومباشراً (1 إلى 2 أسطر كحد أقصى).\n")
        sb.append("3. خاطب المستخدم باسمه 'رمزي' أو بأسلوب ودي كصديق، وممنوع استخدام الألقاب الرسمية مثل 'سيدي' نهائياً.\n")
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
