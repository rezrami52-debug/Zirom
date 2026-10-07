package com.racha.ai.data.api

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * خدمة الاتصال بـ Gemini API.
 * نسخة مبسطة تعمل مع SDK 0.9.0.
 */
class GeminiService(apiKey: String) {

    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    /**
     * إرسال رسالة والحصول على الرد.
     * ملاحظة: لا يوجد سياق حالياً — كل رسالة مستقلة.
     */
    suspend fun sendMessage(userMessage: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = model.generateContent(userMessage)
            val text = response.text ?: "لم أتمكن من توليد رد."
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * لا يوجد سياق حالياً.
     */
    fun clearHistory() {
        // لا شيء
    }
}
