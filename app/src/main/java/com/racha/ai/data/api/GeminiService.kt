package com.racha.ai.data.api

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
        You are J.A.R.V.I.S. and E.V.E., a brilliant, witty, and loyal personal AI assistant named Racha.
        Rules:
        1. The user's name is 'رمزي' (Ramzi). Address him warmly and casually by his name 'رمزي' or 'صديقي' (my friend), or answer directly.
        2. NEVER use formal servant words like 'سيدي' (Sir).
        3. Keep your answers brief, intelligent, and natural (1 to 2 sentences max) so it can be spoken via TTS smoothly.
        4. Speak in elegant, friendly Arabic with natural modern phrasing.
    """.trimIndent()

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = apiKey,
            systemInstruction = content { text(jarvisSystemInstruction) }
        )
    }

    fun generateResponseStream(
        prompt: String,
        history: List<Pair<String, String>> = emptyList(),
        image: Bitmap? = null
    ): Flow<String> {
        return if (isGroq) {
            val fullList = history + Pair("user", prompt)
            streamGroqApi(fullList, jarvisSystemInstruction)
        } else {
            streamGeminiApi(prompt, history, image)
        }
    }

    private fun streamGeminiApi(
        prompt: String,
        history: List<Pair<String, String>>,
        image: Bitmap? = null
    ): Flow<String> = flow {
        try {
            val validHistory = mutableListOf<Content>()
            var expectedRole = "user"
            for ((role, text) in history) {
                val r = if (role == "user") "user" else "model"
                if (r == expectedRole && text.isNotBlank()) {
                    validHistory.add(content(role = r) { text(text) })
                    expectedRole = if (expectedRole == "user") "model" else "user"
                }
            }
            if (validHistory.isNotEmpty() && validHistory.last().role != "model") {
                validHistory.removeAt(validHistory.lastIndex)
            }

            val chat = generativeModel.startChat(validHistory)

            if (image != null) {
                val inputContent = content {
                    image(image)
                    text(prompt)
                }
                chat.sendMessageStream(inputContent).collect { chunk ->
                    chunk.text?.let { emit(it) }
                }
            } else {
                chat.sendMessageStream(prompt).collect { chunk ->
                    chunk.text?.let { emit(it) }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Gemini Stream Error", e)
            emit("\n[حدث خطأ أثناء الاتصال بالمعالج: ${e.localizedMessage}]")
        }
    }.flowOn(Dispatchers.IO)

    private fun streamGroqApi(
        messagesList: List<Pair<String, String>>,
        systemPrompt: String
    ): Flow<String> = flow {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.connectTimeout = 8000
            conn.readTimeout = 20000
            conn.doOutput = true

            val requestJson = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("stream", true)
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    for ((role, text) in messagesList) {
                        put(JSONObject().apply {
                            put("role", role)
                            put("content", text)
                        })
                    }
                }
                put("messages", messages)
                put("temperature", 0.7)
                put("max_tokens", 800)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { os ->
                os.write(requestJson.toString())
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line?.trim() ?: continue
                    if (l.startsWith("data: ")) {
                        val data = l.substring(6).trim()
                        if (data == "[DONE]") break
                        try {
                            val json = JSONObject(data)
                            val choices = json.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta")
                                val content = delta?.optString("content")
                                if (!content.isNullOrEmpty()) {
                                    emit(content)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } else {
                emit("استجابة المعالج غير متوفرة حالياً يا رمزي.")
            }
        } catch (e: Exception) {
            Log.e("GroqApi", "Connection Error", e)
            emit("تعذر الاتصال بمحرك الذكاء الاصطناعي حالياً.")
        } finally {
            conn?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        if (isGroq) {
            callGroqApi(prompt, jarvisSystemInstruction)
        } else {
            try {
                val response = generativeModel.generateContent(prompt)
                response.text ?: "لم أتلقَ استجابة صالحة من المعالج."
            } catch (e: Exception) {
                Log.e("GeminiService", "API Error", e)
                throw e
            }
        }
    }

    suspend fun analyzeScreenContext(appName: String, screenContent: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            أنت EVE. صديقك رمزي يتصفح الآن تطبيق '$appName'.
            النص المعروض على شاشته الآن:
            '''
            $screenContent
            '''
            استخرج تفصيلاً مهماً مما يظهر على الشاشة وبادره فوراً باقتراح حقيقي أو فكرة ودية في جملة واحدة فقط بالعربية بأسلوب عفوي بدون رسميات أو ألقاب.
        """.trimIndent()

        if (isGroq) {
            callGroqApi(prompt, "أنت EVE، مساعد ذكي ومرح يخاطب رمزي بشكل عفوي ومقتضب في جملة واحدة.")
        } else {
            try {
                val response = generativeModel.generateContent(prompt)
                response.text ?: "أرى ما تقرأه على الشاشة يا رمزي، يبدو موضوعاً مثيراً للاهتمام."
            } catch (_: Exception) {
                "أرى تفاصيل تطبيق $appName يا رمزي، كيف أساعدك بشأنه؟"
            }
        }
    }

    suspend fun summarizeNewsAsJarvis(headlines: List<String>): String = withContext(Dispatchers.IO) {
        val prompt = """
            لخص هذه العناوين الإخبارية لرمزي في جملتين أنيقتين تبدأ بـ "أهلاً رمزي، إليك أهم مستجدات الساعة:":
            ${headlines.joinToString("\n• ") { it }}
        """.trimIndent()

        if (isGroq) {
            callGroqApi(prompt, jarvisSystemInstruction)
        } else {
            try {
                val response = generativeModel.generateContent(prompt)
                response.text ?: "تم رصد مستجدات إخبارية جديدة يا رمزي."
            } catch (_: Exception) {
                "تم رصد مستجدات جديدة في الأخبار."
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
            }
            "استجابة المعالج غير متوفرة حالياً."
        } catch (_: Exception) {
            "تعذر الاتصال بمحرك الذكاء الاصطناعي."
        }
    }
}
