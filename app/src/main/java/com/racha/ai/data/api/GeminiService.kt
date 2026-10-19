package com.racha.ai.data.api

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.racha.ai.data.local.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiService(private val apiKey: String) {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    suspend fun sendMessageWithContext(
        history: List<ChatMessageEntity>,
        userMessage: String,
        systemContext: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val chatHistory = history.map { entity ->
                content(role = if (entity.isFromUser) "user" else "model") {
                    text(entity.text)
                }
            }

            val chat = generativeModel.startChat(history = chatHistory)
            val fullPrompt = "$systemContext\n\nالمستخدم: $userMessage"
            
            val response = chat.sendMessage(fullPrompt)
            val textResponse = response.text

            if (!textResponse.isNullOrBlank()) {
                Result.success(textResponse)
            } else {
                Result.success("أهلاً بك يا سيدي، استلمت رسالتك ولكن الاستجابة كانت فارغة، كيف يمكنني مساعدتك أكثر؟")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // إرجاع رد ودي بدلاً من انهيار الطلب
            Result.success("أهلاً بك يا سيدي، أواجه ضغطاً طفيفاً في الاتصال بالشبكة، هل تود تنفيذ أمر محلي كفتح تطبيق أو أداة؟")
        }
    }
}
