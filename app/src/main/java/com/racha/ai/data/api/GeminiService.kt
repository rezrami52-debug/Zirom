package com.racha.ai.data.api

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class GeminiService(apiKey: String) {

    companion object {
        private const val TAG = "RachaGemini"
        private const val MODEL_NAME = "gemini-3.8-flash"
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 2000L
    }

    private val model = GenerativeModel(
        modelName = MODEL_NAME,
        apiKey = apiKey
    )

    suspend fun sendMessage(userMessage: String): Result<String> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        var delayMs = INITIAL_DELAY_MS

        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d(TAG, "محاولة ${attempt + 1}/$MAX_RETRIES")
                val response = model.generateContent(userMessage)
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
                    Log.d(TAG, "انتظار ${delayMs}ms قبل المحاولة التالية")
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
        // لا شيء
    }
}
