package com.racha.ai.data.api

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiService(apiKey: String) {

    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    suspend fun sendMessage(userMessage: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // محاولة بسيطة: إن لم تعمل، سنلجأ إلى HTTP مباشر
            val response = model.generateContent(userMessage)
            val text = response.text ?: "لم أتمكن من توليد رد."
            Result.success(text)
        } catch (e: Exception) {
            // حل احتياطي: إذا فشل SDK، نرسل الطلب يدوياً بالطريقة الصحيحة
            try {
                val url = java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                
                val body = """{"contents":[{"parts":[{"text":"$userMessage"}]}]}"""
                connection.outputStream.write(body.toByteArray())
                
                val responseText = connection.inputStream.bufferedReader().readText()
                val json = org.json.JSONObject(responseText)
                val text = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                Result.success(text)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    fun clearHistory() {
        // لا شيء
    }
}
