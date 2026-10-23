package com.racha.ai.data.api

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

class GeminiService(private val apiKey: String) {

    // شخصية JARVIS
    private val jarvisSystemInstruction = """
        You are J.A.R.V.I.S., the highly intelligent, witty, loyal, and sophisticated AI assistant from Iron Man.
        Guidelines:
        1. Address the user with respect as 'سيدي' (Sir) or Mr. Stark if appropriate, in a natural, polite, and calm tone.
        2. Keep your answers concise, razor-sharp, and informative. Avoid unnecessary fluff.
        3. Match the user's language: If they speak in Arabic, reply in polished, articulate Arabic with a touch of elegance.
        4. If asked about system operations, speak as an integrated AI operating system.
    """.trimIndent()

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = apiKey,
            systemInstruction = content { text(jarvisSystemInstruction) }
        )
    }

    suspend fun generateResponse(prompt: String): String {
        return try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "سيدي، لم أتلقَ بيانات صالحة من المعالج."
        } catch (e: Exception) {
            throw e
        }
    }
}
