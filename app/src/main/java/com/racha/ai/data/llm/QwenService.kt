package com.racha.ai.data.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.racha.ai.data.local.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Qwen محلي عبر MediaPipe.
 */
class QwenService(private val context: Context) : LLMProvider {

    companion object {
        private const val TAG = "RachaQwen"
        private const val MODEL_FILENAME = "qwen2.5-1.5b-instruct.task"
        private const val MODEL_PATH = "/storage/emulated/0/Racha/models/$MODEL_FILENAME"
        private const val MAX_TOKENS = 512
        private const val TEMPERATURE = 0.7f
        private const val TOP_K = 40
    }

    override val name: String = "Qwen (محلي)"

    private var llmInference: LlmInference? = null
    private var isInitialized = false

    /**
     * تهيئة النموذج (تستغرق وقتاً).
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) return@withContext Result.success(Unit)

            val modelFile = File(MODEL_PATH)
            if (!modelFile.exists()) {
                return@withContext Result.failure(
                    Exception("النموذج غير موجود في: $MODEL_PATH")
                )
            }

            Log.d(TAG, "تحميل النموذج من: $MODEL_PATH")
            val startTime = System.currentTimeMillis()

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(MODEL_PATH)
                .setMaxTokens(MAX_TOKENS)
                .setTopK(TOP_K)
                .setTemperature(TEMPERATURE)
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

            // بناء الـ prompt الكامل
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

    /**
     * بناء prompt من السياق.
     * Qwen يستخدم ChatML format.
     */
    private fun buildPrompt(
        history: List<ChatMessageEntity>,
        userMessage: String,
        userProfileContext: String
    ): String {
        val sb = StringBuilder()

        // System prompt
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

        // History (آخر 6 رسائل فقط - Qwen 1.5B محدود)
        val recentHistory = history.takeLast(6)
        for (msg in recentHistory) {
            val role = if (msg.isFromUser) "user" else "assistant"
            sb.append("<|im_start|>$role\n")
            sb.append(msg.text)
            sb.append("<|im_end|>\n")
        }

        // Current message
        sb.append("<|im_start|>user\n")
        sb.append(userMessage)
        sb.append("<|im_end|>\n")

        // Assistant prefix
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
