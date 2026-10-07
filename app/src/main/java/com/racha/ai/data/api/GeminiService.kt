package com.racha.ai.data.api

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.asTextOrNull
import com.google.ai.client.generativeai.type.model
import com.google.ai.client.generativeai.type.user
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * خدمة الاتصال بـ Gemini API.
 * تستخدم SDK 0.9.0 مع الواجهة البديلة.
 */
class GeminiService(apiKey: String) {

    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    private val history = mutableListOf<Content>()

    suspend fun sendMessage(userMessage: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // بناء Content بسيط مع role
            val userContent = Content(
                role = "user",
                parts = listOf(
                    com.google.ai.client.generativeai.type.TextPart(userMessage)
                )
            )
            history.add(userContent)

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
            val modelContent = Content(
                role = "model",
                parts = listOf(
                    com.google.ai.client.generativeai.type.TextPart(responseText)
                )
            )
            history.add(modelContent)

            Result.success(responseText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearHistory() {
        history.clear()
    }
}
