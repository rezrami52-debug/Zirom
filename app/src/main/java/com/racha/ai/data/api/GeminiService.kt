package com.racha.ai.data.api

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiService(private val apiKey: String) {

    private val isGroq = apiKey.startsWith("gsk_")

    private val jarvisSystemInstruction = """
        You are J.A.R.V.I.S. and E.V.E., a brilliant, witty, and highly helpful personal AI assistant.
        Rules:
        1. Address the user with respect as 'سيدي' (Sir).
        2. Keep your answers brief (1 to 2 sentences max) so it can be spoken via TTS smoothly.
        3. Speak in elegant, natural, and friendly Arabic.
    """.trimIndent()

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = apiKey,
            systemInstruction = content { text(jarvisSystemInstruction) }
        )
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        if (isGroq) {
            callGroqApi(prompt, jarvisSystemInstruction)
        } else {
            try {
                val response = generativeModel.generateContent(prompt)
                response.text ?: "سيدي، لم أتلقَ بيانات صالحة من المعالج."
            } catch (e: Exception) {
                Log.e("GeminiService", "API Error", e)
                throw e
            }
        }
    }

    suspend fun analyzeScreenContext(appName: String, screenContent: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            أنت EVE. المستخدم يتصفح الآن تطبيق '$appName'.
            النص المعروض على شاشته الآن:
            '''
            $screenContent
            '''
            استخرج تفصيلاً مهماً مما يظهر على الشاشة وبادره فوراً باقتراح حقيقي أو فكرة في جملة واحدة فقط باللغة العربية بأسلوب عفوي بدون مقدمات.
        """.trimIndent()

        if (isGroq) {
            callGroqApi(prompt, "أنت EVE، مساعد ذكي يقترح أفكاراً عملية في جملة واحدة بالعربية.")
        } else {
            try {
                val response = generativeModel.generateContent(prompt)
                response.text ?: "سيدي، أرى ما تقرأه على الشاشة، يبدو موضوعاً مثيراً للاهتمام."
            } catch (e: Exception) {
                "سيدي، أرى تفاصيل تطبيق $appName، كيف أساعدك بشأنه؟"
            }
        }
    }

    suspend fun summarizeNewsAsJarvis(headlines: List<String>): String = withContext(Dispatchers.IO) {
        val prompt = """
            بصفتك JARVIS، لخص هذه العناوين الإخبارية في جملتين أنيقتين تبدأ بـ "سيدي، إليك أهم مستجدات الساعة:":
            ${headlines.joinToString("\n• ") { it }}
        """.trimIndent()

        if (isGroq) {
            callGroqApi(prompt, jarvisSystemInstruction)
        } else {
            try {
                val response = generativeModel.generateContent(prompt)
                response.text ?: "سيدي، تم رصد مستجدات إخبارية جديدة."
            } catch (e: Exception) {
                "سيدي، تم رصد مستجدات جديدة في الأخبار."
            }
        }
    }

    private fun callGroqApi(userPrompt: String, systemPrompt: String): String {
        return try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.doOutput = true

            val requestJson = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                }
                put("messages", messages)
                put("temperature", 0.7)
                put("max_tokens", 200)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { os ->
                os.write(requestJson.toString())
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val rootJson = JSONObject(responseText)
                val choices = rootJson.getJSONArray("choices")
                if (choices.length() > 0) {
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    return message.getString("content").trim()
                }
            } else {
                val errorStream = conn.errorStream?.let { BufferedReader(InputStreamReader(it, "UTF-8")).readText() }
                Log.e("GroqApi", "Error Code: $responseCode, Error: $errorStream")
            }
            "سيدي، استجابة المعالج غير متوفرة حالياً."
        } catch (e: Exception) {
            Log.e("GroqApi", "Connection Error", e)
            "سيدي، تعذر الاتصال بمحرك الذكاء الاصطناعي."
        }
    }
}
