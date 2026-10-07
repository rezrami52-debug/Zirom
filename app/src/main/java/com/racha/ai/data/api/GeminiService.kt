package com.racha.ai.data.api

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.asTextOrNull
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * خدمة الاتصال بـ Gemini API.
 * تدير المحادثة وتحتفظ بالسياق.
 */
class GeminiService(apiKey: String) {

    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    private val history = mutableListOf<Content>()

    /**
     * إرسال رسالة والحصول على الرد.
     */
    suspend fun sendMessage(userMessage: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // أضف رسالة المستخدم إلى السياق
            history.add(content(role = "user") { text(userMessage) })

            // إرسال للموديل
            val response = model.generateContent(history.toList())
            val responseText = response.candidates
                .firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.asTextOrNull()
                ?: "لم أتمكن من توليد رد."

            // أضف رد Gemini إلى السياق
            history.add(content(role = "model") { text(responseText) })

            Result.success(responseText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * مسح تاريخ المحادثة.
     */
    fun clearHistory() {
        history.clear()
    }
}
