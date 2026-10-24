package com.racha.ai.core.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object WebSearchEngine {

    /**
     * الإجابة على استشارات العشاء والصحة محلياً وفورياً
     */
    fun getLocalAdviceOrMeal(query: String): String? {
        val lower = query.lowercase()

        // استشارات وجبة العشاء والأكل
        if (lower.contains("عشاء") || lower.contains("العشاء") || lower.contains("اكل") || lower.contains("أكل") || lower.contains("وجبة") || lower.contains("جائع")) {
            val dinnerSuggestions = listOf(
                "سيدي، لوجبة عشاء صحية وخفيفة لا تؤرق نومك، أقترح عليك:\n• سلطة تونة غنية بالبروتين مع زيت الزيتون والليمون 🥗\n• أو زبادي طبيعي مع القليل من الشوفان والمكسرات 🥣\n• مع كوب من البابونج أو الحليب الدافئ لنوم هادئ 🌙.",
                "سيدي، بما أن الوقت متأخر، من الأفضل تجنب الوجبات الدسمة. ما رأيك في:\n• شطيرة جبن أبيض أو بيض مسلوق مع خبز كامل 🥪\n• طبق حساء دافئ وخفيف 🍲\nستمنحك الشبع دون إثقال المعدة.",
                "أقترح عليك سيدي عشاءً سريعاً ولذيذاً:\n• عجة بيض بالأعشاب والخضار الخفيفة 🍳\n• مع حبة فاكهة مهدئة كالموز أو الكيوي لمساعدتك على استرخاء العضلات 🍌."
            )
            return dinnerSuggestions.random()
        }

        // استشارات النوم والأرق
        if (lower.contains("نوم") || lower.contains("أرق") || lower.contains("ارق") || lower.contains("تعب")) {
            return "سيدي، لمساعدتك على النوم العميق أنصحك بـ:\n1. إبعاد هاتفك وتخفيف الإضاءة الزرقاء 📵\n2. شرب كوب ماء فاتر أو أعشاب مهدئة 🍵\n3. خفض حرارة الغرفة قليلاً. ليلة هانئة سيدي!"
        }

        return null
    }

    /**
     * البحث المباشر في الويب وجلب خلاصة المعرفة بدون أي مفتاح API
     */
    suspend fun searchWeb(query: String): String = withContext(Dispatchers.IO) {
        // أولاً: فحص إن كان سؤالاً حياتياً يمكن الإجابة عليه محلياً
        val localResponse = getLocalAdviceOrMeal(query)
        if (localResponse != null) return@withContext localResponse

        // ثانياً: البحث عبر ويكيبيديا ومحرك الويب المفتوح
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://ar.wikipedia.org/api/rest_v1/page/summary/$encodedQuery")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "RachaAI/1.0")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val extract = json.optString("extract", "")
                if (extract.isNotBlank()) {
                    return@withContext "سيدي، وفقاً للمعلومات الموثقة:\n$extract"
                }
            }
        } catch (e: Exception) {
            // تجاهل خطأ الويب والانتقال للرد الذكي
        }

        "سيدي، بحثت في الويب بخصوص '$query'. أنصحك بالاطلاع على التفاصيل الكاملة في المتصفح، وسأكون معك لأي تلخيص تحتاجه."
    }
}
