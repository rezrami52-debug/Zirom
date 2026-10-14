package com.racha.ai.data.search

import android.content.Context
import android.util.Log
import com.racha.ai.core.agent.IntentMatcher
import com.racha.ai.data.llm.QwenService
import com.racha.ai.data.local.KnowledgeEntity
import com.racha.ai.data.repository.KnowledgeRepository

/**
 * يُنسّق: DB → Qwen → Web Search → Qwen → حفظ.
 */
class KnowledgeAugmenter(
    context: Context,
    private val qwenService: QwenService
) {

    companion object {
        private const val TAG = "RachaAugmenter"

        // كلمات تدل على أن السؤال يحتاج بحثاً
        private val SEARCH_TRIGGERS = listOf(
            "كيف", "طريقة", "خطوات",
            "كود", "برمجة", "دالة", "مكتبة",
            "أحدث", "جديد", "2024", "2025", "2026",
            "ما هو", "ما هي", "ماذا",
            "أخبار", "حدث", "اليوم"
        )

        // كلمات تدل على أن Qwen لا يعرف
        private val DONT_KNOW = listOf(
            "لا أعلم", "لا أعرف", "لست متأكد",
            "لا يمكنني", "لا استطيع", "لا أستطيع",
            "I don't know", "I cannot"
        )
    }

    private val knowledgeRepo = KnowledgeRepository(context)

    /**
     * إجابة ذكية: DB → Qwen → Web → حفظ.
     */
    suspend fun smartAnswer(
        question: String,
        userProfileContext: String = "",
        history: List<com.racha.ai.data.local.ChatMessageEntity> = emptyList()
    ): AugmentResult {

        Log.d(TAG, "سؤال: $question")

        // ===== 1. ابحث في قاعدة المعرفة =====
        val cached = knowledgeRepo.findBestMatch(question)
        if (cached != null) {
            Log.d(TAG, "✅ وُجد في DB: ${cached.question}")
            knowledgeRepo.markUsed(cached.id)
            return AugmentResult(
                answer = cached.answer,
                source = "knowledge",
                fromCache = true
            )
        }

        // ===== 2. اسأل Qwen =====
        Log.d(TAG, "لا يوجد في DB — اسأل Qwen")
        val qwenResult = qwenService.sendMessageWithContext(
            history = history,
            userMessage = question,
            userProfileContext = userProfileContext
        )

        val qwenAnswer = qwenResult.getOrNull()

        // هل Qwen يعرف؟
        val qwenKnows = qwenAnswer != null && !isDontKnow(qwenAnswer)

        if (qwenKnows && !needsSearch(question)) {
            Log.d(TAG, "✅ Qwen أجاب")
            return AugmentResult(
                answer = qwenAnswer!!,
                source = "qwen",
                fromCache = false
            )
        }

        // ===== 3. ابحث على الويب =====
        Log.d(TAG, "⚠️ Qwen لا يعرف أو السؤال يحتاج بحث — نبحث")
        val searchResults = WebSearchService.search(question, maxResults = 5)

        if (searchResults.isEmpty()) {
            Log.w(TAG, "لا نتائج بحث — نعيد Qwen")
            return AugmentResult(
                answer = qwenAnswer ?: "⚠️ لم أتمكن من الإجابة.",
                source = "qwen_fallback",
                fromCache = false
            )
        }

        // ===== 4. اجلب نص أول صفحتين =====
        val enrichedResults = searchResults.take(3).map { result ->
            val fullText = WebSearchService.fetchPageText(result.url, maxChars = 2000)
            result.copy(fullText = fullText)
        }

        // ===== 5. اطلب من Qwen أن يلخّص =====
        Log.d(TAG, "اطلب من Qwen تلخيص ${enrichedResults.size} نتائج")
        val summary = summarizeWithQwen(question, enrichedResults, userProfileContext)

        // ===== 6. احفظ في DB =====
        val finalAnswer = summary ?: buildFallbackAnswer(enrichedResults)

        knowledgeRepo.quickSave(
            question = question,
            answer = finalAnswer,
            topic = extractTopic(question),
            keywords = extractKeywords(question),
            source = "web"
        )

        Log.d(TAG, "✅ تم الحفظ في DB")

        return AugmentResult(
            answer = finalAnswer,
            source = "web",
            fromCache = false
        )
    }

    /**
     * تلخيص النتائج بـ Qwen.
     */
    private suspend fun summarizeWithQwen(
        question: String,
        results: List<SearchResult>,
        userProfileContext: String
    ): String? {
        val sourcesText = results.mapIndexed { index, r ->
            """
            [المصدر ${index + 1}: ${r.title}]
            ${r.snippet}
            ${r.fullText?.take(800) ?: ""}
            """.trimIndent()
        }.joinToString("\n\n")

        val prompt = """
            السؤال: $question
            
            نتائج البحث:
            $sourcesText
            
            المطلوب:
            - لخّص الإجابة بالعربية الفصحى.
            - 2-3 أسطر فقط.
            - لا تستخدم الصينية أو الإنجليزية.
            - إذا كانت النتائج غير واضحة، قل "لم أجد إجابة واضحة".
        """.trimIndent()

        val result = qwenService.sendMessageWithContext(
            history = emptyList(),
            userMessage = prompt,
            userProfileContext = ""
        )

        return result.getOrNull()
    }

    /**
     * إجابة بديلة من النتائج المختصرة (إن فشل Qwen).
     */
    private fun buildFallbackAnswer(results: List<SearchResult>): String {
        val sb = StringBuilder()
        sb.append("وجدت المعلومات التالية:\n\n")
        results.take(2).forEach { r ->
            sb.append("• ${r.snippet.take(200)}\n")
            sb.append("  المصدر: ${r.url}\n\n")
        }
        return sb.toString().trim()
    }

    /**
     * هل السؤال يحتاج بحث؟
     */
    private fun needsSearch(question: String): Boolean {
        val normalized = IntentMatcher.normalize(question)
        return SEARCH_TRIGGERS.any { trigger ->
            normalized.contains(IntentMatcher.normalize(trigger))
        }
    }

    /**
     * هل Qwen قال "لا أعلم"؟
     */
    private fun isDontKnow(answer: String): Boolean {
        return DONT_KNOW.any { answer.contains(it, ignoreCase = true) }
    }

    /**
     * استخراج موضوع من السؤال.
     */
    private fun extractTopic(question: String): String {
        // بسيط جداً — يمكن تحسينه لاحقاً
        return question.take(30)
    }

    /**
     * استخراج كلمات مفتاحية.
     */
    private fun extractKeywords(question: String): String {
        val stopWords = setOf(
            "ما", "هو", "هي", "من", "في", "على", "إلى", "عن",
            "كيف", "متى", "أين", "لماذا", "هل", "الذي", "التي",
            "the", "a", "an", "is", "are", "was", "were", "what", "how"
        )

        return question
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it.lowercase() !in stopWords }
            .joinToString(",")
    }
}

/**
 * نتيجة الإجابة.
 */
data class AugmentResult(
    val answer: String,
    val source: String,        // "knowledge", "qwen", "web", "qwen_fallback"
    val fromCache: Boolean
)
