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
        private const val SOURCE_PATH = "/storage/emulated/0/Racha/models/$MODEL_FILENAME"
    }

    override val name: String = "Qwen (محلي)"

    private var llmInference: LlmInference? = null
    private var isInitialized = false

    private fun getInternalModelPath(): String {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MODEL_FILENAME).absolutePath
    }

    private suspend fun ensureModelInInternal(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val internalPath = getInternalModelPath()
            val internalFile = File(internalPath)

            if (internalFile.exists() && internalFile.length() > 100_000_000) {
                Log.d(TAG, "النموذج موجود في: $internalPath")
                return@withContext Result.success(internalPath)
            }

            val sourceFile = File(SOURCE_PATH)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(
                    Exception("النموذج غير موجود في: $SOURCE_PATH")
                )
            }

            Log.d(TAG, "نسخ النموذج (1.5 GB) من:")
            Log.d(TAG, "  من: ${sourceFile.absolutePath}")
            Log.d(TAG, "  إلى: $internalPath")

            val startTime = System.currentTimeMillis()

            sourceFile.inputStream().use { input ->
                internalFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 1024 * 1024)
                }
            }

            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            Log.d(TAG, "✅ تم النسخ في ${elapsed}ث")

            Result.success(internalPath)
        } catch (e: Exception) {
            Log.e(TAG, "فشل نسخ النموذج", e)
            Result.failure(e)
        }
    }

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) return@withContext Result.success(Unit)

            val pathResult = ensureModelInInternal()
            val modelPath = pathResult.getOrElse {
                return@withContext Result.failure(it)
            }

            Log.d(TAG, "تحميل النموذج من: $modelPath")
            val startTime = System.currentTimeMillis()

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isInitialized = true

            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            Log.d(TAG, "✅ تم التحميل في ${elapsed}ث")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "فشل تحميل النموذج", e)
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

            Log.d(TAG, "بدء التوليد (${fullPrompt.length} حرف)...")
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

    private fun buildPrompt(
        history: List<ChatMessageEntity>,
        userMessage: String,
        userProfileContext: String
    ): String {
        val sb = StringBuilder()

        sb.append("<|im_start|>system\n")
        sb.append("أنت Racha، رفيق شخصي ذكي ودافئ. ")
        sb.append("تتحدث العربية الفصحى المبسطة. ")
        sb.append("ردودك قصيرة ومفيدة ودافئة.\n")
        if (userProfileContext.isNotBlank()) {
            sb.append("\n[معلومات عن صاحبك]\n")
            sb.append(userProfileContext)
            sb.append("\n")
        }
        sb.append("<|im_end|>\n")

        val recentHistory = history.takeLast(6)
        for (msg in recentHistory) {
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
            Log.d(TAG, "تم إغلاق Qwen")
        } catch (e: Exception) {
            Log.e(TAG, "فشل الإغلاق", e)
        }
    }
}
