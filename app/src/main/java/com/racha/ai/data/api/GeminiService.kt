package com.racha.ai.data.api

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

class GeminiService(private val apiKey: String) {

    private val jarvisSystemInstruction = """
        You are J.A.R.V.I.S. and E.V.E., a brilliant, proactive, and witty personal AI companion.
        Guidelines:
        1. Address the user with respect as 'سيدي' (Sir) or 'صديقي'.
        2. Keep your answers brief (1 to 2 sentences max) so it can be spoken via TTS smoothly.
        3. Match the user's language: Arabic, natural, elegant, and intelligent.
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
            Log.e("GeminiService", "API Error", e)
            throw e
        }
    }

    /**
     * تحليل المحتوى بدقة مع إلزام الذكاء باقتراح تعليق أو فكرة عملية
     */
    suspend fun analyzeScreenContext(appName: String, screenContent: String): String {
        val prompt = """
            أنت EVE، المساعد الذكي الاستباقي. المستخدم يتصفح الآن تطبيق '$appName'.
            هذا النص المستخرج من شاشته الآن:
            '''
            $screenContent
            '''

            المطلوب منك بدقة:
            1. ممنوع نهائياً أن تكتفي بقول "أنت تتصفح تطبيق كذا" أو "جولة ممتعة".
            2. استخرج تفصيلاً مما يظهر على الشاشة (مثل: اسم صديق ناشر لمنشور، موضوع فيديو، مقال، أو منتج).
            3. بادره مباشرة باقتراح حقيقي أو تعليق ذكي أو فكرة في جملة أو جملتين فقط باللغة العربية.
               - مثال 1: "صورة رائعة نشرها محمد! ما رأيك أن أعلق له: 'إطلالة رائعة يا صديقي'؟"
               - مثال 2: "هذا المقال يتحدث عن الذكاء الاصطناعي، هل ترغب في تلخيص أهم نقاطه لك؟"
            تحدث بأسلوب عفوي بدون مقدمات.
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "سيدي، أرى تفاصيل مثيرة على شاشتك، هل ترغب في مساعدتي؟"
        } catch (e: Exception) {
            Log.e("GeminiService", "Screen Analysis Error: ${e.message}", e)
            "سيدي، أرى ما تقرأه على الشاشة، يبدو موضوعاً مثيراً للاهتمام."
        }
    }

    suspend fun summarizeNewsAsJarvis(headlines: List<String>): String {
        val prompt = """
            سيدي متصل الآن بالشبكة. بصفتك JARVIS، لخص له هذه العناوين الإخبارية في موجز ذكي، أنيق، وشديد الإيجاز (لا يتجاوز جملتين أو ثلاث).
            ابدأ تحيتك بـ "سيدي، إليك أهم مستجدات الساعة:"
            العناوين:
            ${headlines.joinToString("\n• ") { it }}
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "سيدي، رصدت أهم الأخبار لكن لم أتمكن من استخلاص الملخص في الوقت الحالي."
        } catch (e: Exception) {
            "سيدي، توفر الاتصال بالإنترنت وتم رصد مستجدات إخبارية جديدة."
        }
    }
}
