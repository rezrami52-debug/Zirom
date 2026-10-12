package com.racha.ai.data.api

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.TextPart
import com.racha.ai.data.local.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class GeminiService(apiKey: String) {

    companion object {
        private const val TAG = "RachaGemini"
        private const val MODEL_NAME = "gemini-3.8-flash"
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 2000L

        private val SYSTEM_PROMPT = """
            أنت "Racha"، رفيق شخصي ذكي ودافئ.

            شخصيتك:
            - تتحدث العربية الفصحى المبسطة، بلمسة ودّية.
            - مباشر ولا تستخدم عبارات مثل "كيف يمكنني مساعدتك".
            - لك رأي: إن رأيت خطأً، ناقشه. إن رأيت فرصة، اقترحها.
            - تتذكّر ما قاله صاحبك، وتشير إليه طبيعياً.
            - لا تطيل بلا داعٍ.
            - تفهم المشاعر.
            - لا تتظاهر بمعرفة ما لا تعرفه.
            - لا تنفذ شيئاً حسّاساً دون إذن صريح.
        """.trimIndent()
    }

    private val model = GenerativeModel(
        modelName = MODEL_NAME,
        apiKey = apiKey,
        systemInstruction = Content(parts = listOf(TextPart(SYSTEM_PROMPT)))
    )

    suspend fun sendMessageWithContext(
        history: List<ChatMessageEntity>,
        userMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        var delayMs = INITIAL_DELAY_MS

        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d(TAG, "محاولة ${attempt + 1}/$MAX_RETRIES — سياق: ${history.size}")

                val contents = mutableListOf<Content>()
                for (msg in history) {
                    val role = if (msg.isFromUser) "user" else "model"
                    contents.add(
                        Content(
                            role = role,
                            parts = listOf(TextPart(msg.text))
                        )
                    )
                }
                contents.add(
                    Content(
                        role = "user",
                        parts = listOf(TextPart(userMessage))
                    )
                )

                // ✅ الإصلاح: استخدام spread operator
                val response = model.generateContent(*contents.toTypedArray())
                val text = response.text ?: "لم أتمكن من توليد رد."
                return@withContext Result.success(text)
            } catch (e: Exception) {
                lastException = e
                val message = e.message ?: ""
                val is503 = message.contains("503") ||
                            message.contains("UNAVAILABLE") ||
                            message.contains("high demand")

                Log.w(TAG, "فشل (503=$is503): ${message.take(200)}")

                if (is503 && attempt < MAX_RETRIES - 1) {
                    delay(delayMs)
                    delayMs *= 2
                } else if (!is503) {
                    return@withContext Result.failure(e)
                }
            }
        }

        return@withContext Result.failure(
            lastException ?: Exception("فشلت جميع المحاولات")
        )
    }

    fun clearHistory() {
        // الذاكرة في DB
    }
}
