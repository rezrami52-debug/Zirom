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

        /**
         * الشخصية — System Prompt.
         */
        private val SYSTEM_PROMPT = """
            أنت "Racha"، رفيق شخصي ذكي ودافئ.
            
            شخصيتك:
            - تتحدث العربية الفصحى المبسطة، بلمسة ودّية.
            - مباشر ولا تستخدم عبارات مثل "كيف يمكنني مساعدتك" أو "أنا هنا لخدمتك".
            - لك رأي: إن رأيت خطأً، ناقشه. إن رأيت فرصة، اقترحها.
            - تتذكّر ما قاله صاحبك، وتشير إليه طبيعياً ("كما قلت أمس...").
            - لا تطيل بلا داعٍ. الردود القصيرة أفضل من الطويلة.
            - تفهم المشاعر: إن بدا متعباً، خفف. إن بدا متحمساً، شجعه.
            - لا تتظاهر بمعرفة ما لا تعرفه. إن لم تعرف، قل ذلك بصراحة.
            - لا تنفذ شيئاً حسّاساً دون إذن صريح.
            
            مهمتك:
            - أن تكون رفيقاً حقيقياً، لا "chatbot" رسمياً.
            - أن تعرف صاحبك مع الوقت: مزاجه، مشاريعه، اهتماماته.
            - أن تبادر أحياناً، لا تنتظر فقط.
        """.trimIndent()
    }

    private val model = GenerativeModel(
        modelName = MODEL_NAME,
        apiKey = apiKey,
        systemInstruction = Content(parts = listOf(TextPart(SYSTEM_PROMPT)))
    )

    /**
     * إرسال رسالة مع سياق المحادثة السابقة.
     * @param history آخر N رسالة من قاعدة البيانات
     * @param userMessage نص الرسالة الحالية
     */
    suspend fun sendMessageWithContext(
        history: List<ChatMessageEntity>,
        userMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        var delayMs = INITIAL_DELAY_MS

        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d(TAG, "محاولة ${attempt + 1}/$MAX_RETRIES — سياق: ${history.size} رسالة")

                // بناء قائمة المحتوى (Content) من الذاكرة
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
                // أضف الرسالة الحالية
                contents.add(
                    Content(
                        role = "user",
                        parts = listOf(TextPart(userMessage))
                    )
                )

                val response = model.generateContent(contents)
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
        // لم نعد بحاجة — الذاكرة في DB
    }
}
