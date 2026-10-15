package com.racha.ai.core.context

import android.util.Log

/**
 * ردود ذكية مبرمجة لأوامر شائعة.
 * تعمل بدون LLM — فورية.
 */
object SmartReplies {

    private const val TAG = "RachaSmartReplies"

    /**
     * محاولة إيجاد رد ذكي.
     * @return الرد إذا وُجد، أو null (يذهب لـ LLM).
     */
    fun tryReply(
        input: String,
        context: ContextSnapshot
    ): String? {
        val normalized = normalize(input)

        // ===== "اذا" و "ماذا الآن" =====
        if (matches(normalized, "اذا", "واذا", "ماذا الان", "ماذا بعد", "ثم ماذا")) {
            return handleIfThen(context)
        }

        // ===== "حسنا" / "تمام" / "طيب" =====
        if (matches(normalized, "حسنا", "حسن", "تمام", "طيب", "اوكي", "ماشي")) {
            return handleAcknowledgment(context)
        }

        // ===== "لا" / "لا شكرا" =====
        if (normalized == "لا" || matches(normalized, "لا شكرا", "ليس الان", "لا اريد")) {
            return handleRefusal()
        }

        // ===== "نعم" / "اجل" =====
        if (normalized == "نعم" || matches(normalized, "اجل", "بلا شك", "طبعا")) {
            return "ممتاز! أخبرني ماذا تريد."
        }

        // ===== "كيف حالك" / "اخبارك" =====
        if (matches(normalized, "كيف حالك", "اخبارك", "كيفك", "شلونك")) {
            return "أنا بخير، شكراً! ${context.greeting} كيف حالك أنت؟"
        }

        // ===== "شكرا" =====
        if (matches(normalized, "شكرا", "مشكور", "بارك الله")) {
            return "العفو! 🌟 أنا هنا دائماً."
        }

        // ===== "صباح الخير" / "مساء الخير" =====
        if (matches(normalized, "صباح الخير")) {
            return "صباح النور! ☀️ ${getMorningSuggestion(context)}"
        }

        if (matches(normalized, "مساء الخير", "مساء النور")) {
            return "مساء النور! 🌆 كيف كان يومك؟"
        }

        // ===== "تصبح على خير" / "نام" =====
        if (matches(normalized, "تصبح على خير", "سنام", "اريد النوم")) {
            return "طابت ليلتك 🌙 نم جيداً، أراك غداً."
        }

        // ===== لا يوجد رد ذكي =====
        return null
    }

    /**
     * معالجة "اذا" / "ماذا الآن".
     */
    private fun handleIfThen(context: ContextSnapshot): String {
        val sb = StringBuilder()

        // إذا عاد بعد فجوة
        if (context.timeSinceLastChat != "الآن" && context.timeSinceLastChat != "أول محادثة") {
            sb.append("أهلاً بعودتك! ")

            if (context.lastTopic.isNotBlank()) {
                sb.append("كنّا نتحدث عن: \"${context.lastTopic}\". ")
                sb.append("نُكمل؟")
            } else {
                sb.append("كيف يمكنني مساعدتك الآن؟")
            }
        } else {
            // نفس الجلسة
            if (context.lastTopic.isNotBlank()) {
                sb.append("نحن في موضوع: \"${context.lastTopic}\". ")
                sb.append("ماذا تريد أن نفعل الآن؟")
            } else {
                sb.append("ماذا تريد أن نفعل الآن؟")
            }
        }

        return sb.toString()
    }

    /**
     * معالجة "حسنا" / "تمام".
     */
    private fun handleAcknowledgment(context: ContextSnapshot): String {
        val options = listOf(
            "ممتاز! ماذا بعد؟",
            "جيد! أخبرني المزيد.",
            "تمام. ماذا تريد الآن؟",
            "حسناً. أنا جاهز."
        )
        return options.random()
    }

    /**
     * معالجة الرفض.
     */
    private fun handleRefusal(): String {
        return "حسناً، لا مشكلة. أخبرني متى تحتاجني."
    }

    /**
     * اقتراح صباحي.
     */
    private fun getMorningSuggestion(context: ContextSnapshot): String {
        return "هل تناولت فطورك؟"
    }

    /**
     * تطبيع النص العربي.
     */
    private fun normalize(input: String): String {
        return input
            .lowercase()
            .trim()
            .replace("إ", "ا")
            .replace("أ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "") // إزالة التشكيل
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * مطابقة كلمة أو عبارة.
     */
    private fun matches(text: String, vararg keywords: String): Boolean {
        val normalizedText = normalize(text)
        return keywords.any { keyword ->
            normalizedText == normalize(keyword) ||
            normalizedText.contains(normalize(keyword))
        }
    }
}
