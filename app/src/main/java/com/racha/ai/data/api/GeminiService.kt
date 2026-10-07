package com.racha.ai.data.api

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * خدمة الاتصال بـ Gemini API.
 * نستخدم gemini-3.8-flash — الأحدث والمتوافق مع SDK 0.9.0.
 */
class GeminiService(apiKey: String) {

    private val model = GenerativeModel(
        modelName = "gemini-3.8-flash",
        apiKey = apiKey
    )

    suspend fun sendMessage(userMessage: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = model.generateContent(userMessage)
            val text = response.text ?: "لم أتمكن من توليد رد."
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearHistory() {
        // لا شيء
    }
}
