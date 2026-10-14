package com.racha.ai.data.search

import android.content.Context
import android.util.Log
import com.racha.ai.core.agent.IntentMatcher
import com.racha.ai.data.llm.QwenService
import com.racha.ai.data.repository.KnowledgeRepository

class KnowledgeAugmenter(
    context: Context,
    private val qwenService: QwenService
) {

    companion object {
        private const val TAG = "RachaAugmenter"

        private val SEARCH_TRIGGERS = listOf(
            // طلبات
            "كيف", "طريقة", "خطوات", "أريد", "اريد", "أحتاج", "احتاج",
            "أبحث", "ابحث", "أعلمني", "علمني", "اشرح", "وضح",
            // تقنية
            "كود", "برمجة", "دالة", "مكتبة", "تطبيق", "بناء", "إنشاء", "انشاء",
            "تثبيت", "تحميل", "install", "setup", "build", "termux", "ترميكس",
            "android", "أندرويد", "kotlin", "python", "java", "gradle",
            // حديث
            "أحدث", "احدث", "جديد", "2024", "2025", "2026",
            "أخبار", "اخبار", "اليوم",
            // تعريفات
            "ما هو", "ما هي", "ماذا", "من هو", "من هي",
            // حالات
            "لماذا", "متى", "أين", "اين"
        )

        private val DONT_KNOW = listOf(
            "لا أعلم", "لا أعرف", "لست متأكد",
            "لا يمكنني", "لا استطيع", "لا أستطيع",
            "لا أملك معلومات",
            "I don't know", "I cannot", "I'm not sure"
        )

        // الحد الأدنى لطول السؤال للبحث الإجباري
        private const val MIN_LENGTH_FOR_FORCED_SEARCH = 25
    }

    private val knowledgeRepo = KnowledgeRepository(context)

    suspend fun smartAnswer(
        question: String,
        userProfileContext: String = "",
        history: List<com.racha.ai.data.local.ChatMessageEntity> = emptyList()
    ): AugmentResult {

        Log.d(TAG, "سؤال: $question")

        // ===== 1. ابحث في قاعدة المعرفة =====
        val cached = knowledgeRepo.findBestMatch(question)
        if (cached != null) {
            Log.d(TAG, "✅ وُجد في DB")
            knowledgeRepo.markUsed(cached.id)
            return AugmentResult(
                answer = cached.answer,
                source = "knowledge",
                fromCache = true
            )
        }

        // ===== 2. تحديد: هل يجب البحث؟ =====
        val shouldSearch = shouldSearchFirst(question)

        // ===== 3. إذا البحث مطلوب أولاً =====
        if (shouldSearch) {
            Log.d(TAG, "🔍 السؤال يستدعي البحث (trigger)")
            val webResult = tryWebSearch(question, userProfileContext)
            if (webResult != null) {
                return webResult
            }
            Log.w(TAG, "فشل البحث، نعود لـ Qwen")
        }

        // ===== 4. اسأل Qwen =====
        Log.d(TAG, "اسأل Qwen")
        val qwenResult = qwenService.sendMessageWithContext(
            history = history,
            userMessage = question,
            userProfileContext = userProfileContext
        )

        val qwenAnswer = qwenResult.getOrNull()

        // هل Qwen يعرف؟
        val qwenKnows = qwenAnswer != null && !isDontKnow(qwenAnswer)

        if (qwenKnows && qwenAnswer!!.length > 10) {
            Log.d(TAG, "✅ Qwen أجاب")
            return AugmentResult(
                answer = qwenAnswer,
                source = "qwen",
                fromCache = false
            )
        }

        // ===== 5. إذا لم يكن البحث قد تم، ابحث =====
        if (!shouldSearch) {
            Log.d(TAG, "⚠️ Qwen لا يعرف — نبحث")
            val webResult = tryWebSearch(question, userProfileContext)
            if (webResult != null) {
                return webResult
            }
        }

        // ===== 6. فشل كل شيء =====
        return AugmentResult(
            answer = qwenAnswer ?: "⚠️ لم أتمكن من الإجابة.",
            source = "qwen_fallback",
            fromCache = false
        )
    }

    /**
     * هل السؤال يستدعي البحث أولاً؟
     */
    private fun shouldSearchFirst(question: String): Boolean {
        // 1. طول السؤال
        if (question.length >= MIN_LENGTH_FOR_FORCED_SEARCH) {
            // سؤال طويل → بحث (لأن Qwen 1.5B ضعيف)
            val normalized = IntentMatcher.normalize(question)
            // إذا كان فيه كلمة trigger → بحث
            if (SEARCH_TRIGGERS.any { trigger ->
                normalized.contains(IntentMatcher.normalize(trigger))
            }) {
                return true
            }
        }

        // 2. إذا كان فيه trigger مباشر
        val normalized = IntentMatcher.normalize(question)
        return SEARCH_TRIGGERS.any { trigger ->
            normalized.contains(IntentMatcher.normalize(trigger))
        }
    }

    /**
     * محاولة البحث على الويب.
     */
    private suspend fun tryWebSearch(
        question: String,
        userProfileContext: String
    ): AugmentResult? {
        return try {
            val searchResults = WebSearchService.search(question, maxResults = 5)
            if (searchResults.isEmpty()) {
                Log.w(TAG, "لا نتائج")
                return null
            }

            Log.d(TAG, "✅ ${searchResults.size} نتيجة بحث")

            // اجلب أول صفحتين
            val enriched = searchResults.take(3).map { r ->
                val full = WebSearchService.fetchPageText(r.url, maxChars = 1500)
                r.copy(fullText = full)
            }

            // لخّص بـ Qwen
            val summary = summarizeWithQwen(question, enriched)

            val finalAnswer = summary ?: buildFallbackAnswer(enriched)

            // احفظ
            knowledgeRepo.quickSave(
                question = question,
                answer = finalAnswer,
                topic = question.take(30),
                keywords = extractKeywords(question),
                source = "web"
            )

            Log.d(TAG, "✅ تم الحفظ في DB")

            AugmentResult(
                answer = finalAnswer,
                source = "web",
                fromCache = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "فشل البحث", e)
            null
        }
    }

    /**
     * تلخيص النتائج بـ Qwen.
     */
    private suspend fun summarizeWithQwen(
        question: String,
        results: List<SearchResult>
    ): String? {
        val sourcesText = results.mapIndexed { i, r ->
            "[المصدر ${i + 1}: ${r.title}]\n${r.snippet}\n${r.fullText?.take(800) ?: ""}"
        }.joinToString("\n\n")

        val prompt = """
            السؤال: $question
            
            نتائج البحث:
            $sourcesText
            
            المطلوب:
            - لخّص الإجابة بالعربية الفصحى.
            - 3-5 أسطر فقط.
            - لا تستخدم الصينية أو الإنجليزية.
            - إذا كانت النتائج غير واضحة، قل "لم أجد إجابة واضحة".
        """.trimIndent()

        val result = qwenService.sendMessageWithContext(
            history = emptyList(),
            userMessage = prompt,
            userProfileContext = ""
        )

        val answer = result.getOrNull()
        return if (answer != null && answer.length > 20 && !answer.contains("لا أعلم")) {
            answer
        } else null
    }

    private fun buildFallbackAnswer(results: List<SearchResult>): String {
        val sb = StringBuilder()
        sb.append("وجدت المعلومات التالية:\n\n")
        results.take(2).forEach { r ->
            sb.append("• ${r.snippet.take(200)}\n")
            sb.append("  ${r.url}\n\n")
        }
        return sb.toString().trim()
    }

    private fun isDontKnow(answer: String): Boolean {
        return DONT_KNOW.any { answer.contains(it, ignoreCase = true) }
    }

    private fun extractKeywords(question: String): String {
        val stopWords = setOf(
            "ما", "هو", "هي", "من", "في", "على", "إلى", "عن",
            "كيف", "متى", "أين", "لماذا", "هل", "الذي", "التي",
            "the", "a", "an", "is", "are"
        )
        return question
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it.lowercase() !in stopWords }
            .joinToString(",")
    }
}

data class AugmentResult(
    val answer: String,
    val source: String,
    val fromCache: Boolean
)
