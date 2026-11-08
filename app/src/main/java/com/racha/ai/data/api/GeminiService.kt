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

class GeminiService(rawApiKey: String) {

    val apiKey: String = sanitizeApiKey(rawApiKey)
    private val isGroq = apiKey.startsWith("gsk_")

    private val jarvisSystemInstruction = """
        You are J.A.R.V.I.S. and E.V.E., a brilliant, witty personal AI companion named Racha for Ramzi.
        Rules:
        1. Address Ramzi as 'رمزي' or 'صديقي'.
        2. Keep answers brief (1-2 sentences) so TTS is snappy.
        3. Speak in clean, friendly modern Arabic.
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
                response.text ?: "لم أتلقَ استجابة صالحة."
            } catch (e: Exception) {
                Log.e("GeminiService", "API Error", e)
                throw e
            }
        }
    }

    suspend fun summarizeNewsAsJarvis(headlines: List<String>): String = withContext(Dispatchers.IO) {
        val prompt = """
            لخص هذه العناوين الإخبارية لرمزي في جملتين أنيقتين تبدأ بـ "أهلاً رمزي، إليك أهم مستجدات الساعة:":
            ${headlines.joinToString("\n• ") { it }}
        """.trimIndent()

        return@withContext try {
            generateResponse(prompt)
        } catch (_: Exception) {
            "تم رصد مستجدات إخبارية جديدة يا رمزي."
        }
    }

    suspend fun analyzeScreenContext(appName: String, screenContent: String): String = withContext(Dispatchers.IO) {
        val prompt = "أنت EVE. المستخدم رمزي يتصفح تطبيق '$appName'. النص: '$screenContent'. لخص أو اقترح فكرة في جملة واحدة."
        return@withContext try {
            generateResponse(prompt)
        } catch (_: Exception) {
            "أرى تطبيق $appName يا رمزي، كيف أساعدك بشأنه؟"
        }
    }

    private fun callGroqApi(userPrompt: String, systemPrompt: String): String {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            conn.connectTimeout = 3500
            conn.readTimeout = 6000
            conn.doOutput = true

            val requestJson = JSONObject().apply {
                put("model", "openai/gpt-oss-20b")
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
                put("temperature", 0.6)
                put("max_tokens", 250)
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
                val errorStream = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).readText() }
                Log.e("GroqApi", "Error $responseCode: $errorStream")
                throw Exception("كود الخطأ $responseCode: ${errorStream ?: ""}")
            }
            "استجابة المعالج غير متوفرة حالياً."
        } catch (e: Exception) {
            throw e
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (isGroq) {
                val url = URL("https://api.groq.com/openai/v1/models")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36")
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.responseCode == HttpURLConnection.HTTP_OK
            } else {
                val res = generativeModel.generateContent("ping")
                !res.text.isNullOrBlank()
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun sanitizeApiKey(raw: String): String {
            return raw.trim()
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")
                .replace("\t", "")
                .replace("\"", "")
                .replace("'", "")
        }
    }
}
