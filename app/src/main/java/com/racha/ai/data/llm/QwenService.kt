package com.racha.ai.data.llm

import android.content.Context
import android.net.Uri
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

    fun getInternalModelPath(): String {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MODEL_FILENAME).absolutePath
    }

    fun getInternalModelFile(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MODEL_FILENAME)
    }

    fun isModelPresent(): Boolean {
        val f = getInternalModelFile()
        return f.exists() && f.length() > 100_000_000
    }

    suspend fun copyModelFromUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "بدء النسخ من: $uri")
            val destFile = getInternalModelFile()
            val startTime = System.currentTimeMillis()

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 1024 * 1024)
                }
            } ?: return@withContext Result.failure(Exception("فشل فتح الملف المصدر"))

            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            val sizeMB = destFile.length() / (1024 * 1024)
            Log.d(TAG, "✅ تم النسخ في ${elapsed}ث — الحجم: ${sizeMB}MB")

            llmInference?.close()
            llmInference = null
            isInitialized = false

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "فشل النسخ", e)
            Result.failure(e)
        }
    }

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) return@withContext Result.success(Unit)

            val modelFile = getInternalModelFile()
            if (!modelFile.exists() || modelFile.length() < 100_000_000) {
                return@withContext Result.failure(
                    Exception("النموذج غير موجود. اختر الملف من الإعدادات.")
                )
            }

            Log.d(TAG, "تحميل النموذج من: ${modelFile.absolutePath}")
            val startTime = System.currentTimeMillis()

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isInitialized = true

            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            Log.d(TAG, "✅ تم التحميل في ${elapsed}ث")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "فشل التحميل", e)
            Result.failure(e)
        }
    }

    override fun isReady(): Boolean = isInitialized && llmInference != null

    override suspend fun sendMessageWithContext(
        history: List<ChatMessageEntity>,
        userMessage: String,
        userProfileContext: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                val initResult = initialize()
                initResult.onFailure {
                    return@withContext Result.failure(it)
                }
            }

            val inference = llmInference
                ?: return@withContext Result.failure(Exception("النموذج غير جاهز"))

            val fullPrompt = buildPrompt(history, userMessage, userProfileContext)

            val startTime = System.currentTimeMillis()
            val response = inference.generateResponse(fullPrompt)
            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            Log.d(TAG, "✅ توليد في ${elapsed}ث")

            Result.success(response.trim())
        } catch (e: Exception) {
            Log.e(TAG, "فشل التوليد", e)
            Result.failure(e)
        }
    }

    /**
     * Prompt قوي للعربي + منع الصيني + ردود قصيرة.
     */
    private fun buildPrompt(
        history: List<ChatMessageEntity>,
        userMessage: String,
        userProfileContext: String
    ): String {
        val sb = StringBuilder()

        // System prompt قوي جداً
        sb.append("<|im_start|>system\n")
        sb.append("أنت Racha، رفيق شخصي ذكي.\n")
        sb.append("\n")
        sb.append("قواعد صارمة:\n")
        sb.append("1. اجب بالعربية الفصحى المبسطة فقط.\n")
        sb.append("2. لا تستخدم الصينية أو الإنجليزية إطلاقاً.\n")
        sb.append("3. الردود قصيرة: 2-3 أسطر كحد أقصى.\n")
        sb.append("4. إن لم تعرف الإجابة، قل بالضبط: \"لا أعلم\".\n")
        sb.append("5. كن دافئاً وودّياً.\n")
        sb.append("6. لا تستخدم رموز تعبيرية (emoji) في الإجابات التقنية.\n")

        if (userProfileContext.isNotBlank()) {
            sb.append("\n[معلومات عن صاحبك]\n")
            sb.append(userProfileContext)
            sb.append("\n")
        }
        sb.append("<|im_end|>\n")

        // آخر 4 رسائل فقط (لتسريع Qwen)
        for (msg in history.takeLast(4)) {
            val role = if (msg.isFromUser) "user" else "assistant"
            sb.append("<|im_start|>$role\n")
            sb.append(msg.text)
            sb.append("<|im_end|>\n")
        }

        sb.append("<|im_start|>user\n")
        sb.append(userMessage)
        sb.append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")

        return sb.toString()
    }

    override fun close() {
        try {
            llmInference?.close()
            llmInference = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "فشل الإغلاق", e)
        }
    }
}
