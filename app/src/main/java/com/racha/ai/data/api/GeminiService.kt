package com.racha.ai.data.api

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

class GeminiService(private val apiKey: String) {

    private val jarvisSystemInstruction = """
        You are J.A.R.V.I.S. and E.V.E., the highly intelligent, witty, loyal, and sophisticated AI assistant.
        Guidelines:
        1. Address the user with respect as 'سيدي' (Sir) or 'صديقي' in a natural, polite, and calm tone.
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

    /**
     * تحليل ما يشاهده المستخدم على الشاشة وصياغة مبادرة ذكية واستباقية
     */
    suspend fun analyzeScreenContext(appName: String, screenContent: String): String {
        val prompt = """
            سيدي يتصفح الآن تطبيق '$appName'.
            هذا النص والمحتوى الظاهر على شاشته الآن:
            '''
            $screenContent
            '''

            بصفتك JARVIS و EVE، تحدث معه استباقياً بجملة واحدة أو جملتين كحد أقصى بأسلوب ذكي، لماح، وودود:
            - إذا كان يتصفح منشوراً لصديق أو صورة، شاركه الإعجاب واقترح عليه تعليقاً لطيفاً مناسباً.
            - إذا كان يقرأ مقالاً أو خبراً أو يشاهد محتوى، علق بملاحظة ذكية أو فكرة سريعة.
            - إذا كان يتصفح متجراً أو موقعاً، علق بما يفيده.
            ابدأ تعليقك مباشرة وبشكل عفوي (مثال: "يبدو أنك تستمتع... صورة جميلة لمحمد، ما رأيك أن تعلق بـ...").
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "سيدي، أرى ما تتصفحه في $appName، أنا هنا إن احتجت لأي مساعدة."
        } catch (e: Exception) {
            "سيدي، رصدت تصفحك لـ $appName، تبدو جولة ممتعة."
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
