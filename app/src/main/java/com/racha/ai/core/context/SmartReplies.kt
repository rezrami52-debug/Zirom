package com.racha.ai.core.context

object SmartReplies {

    fun tryReply(
        input: String,
        context: ContextSnapshot
    ): String? {
        val normalized = normalize(input)

        // ===== "اذا" / "ماذا الان" =====
        if (matches(normalized, "اذا", "واذا", "ماذا الان", "ماذا بعد", "ثم ماذا")) {
            return handleIfThen(context)
        }

        // ===== "حسنا" / "تمام" =====
        if (matches(normalized, "حسنا", "حسن", "تمام", "طيب", "اوكي", "ماشي")) {
            return handleAcknowledgment()
        }

        // ===== "لماذا" (بحاجة للسياق) =====
        if (normalized == "لماذا" || normalized == "لماذا؟" || matches(normalized, "لماذا هذا")) {
            return handleWhy(context)
        }

        // ===== "كيف" (بحاجة للسياق) =====
        if (normalized == "كيف" || normalized == "كيف؟") {
            return handleHow(context)
        }

        // ===== "ماذا" (بحاجة للسياق) =====
        if (normalized == "ماذا" || normalized == "ماذا؟") {
            return handleWhat(context)
        }

        // ===== "لا" =====
        if (normalized == "لا" || matches(normalized, "لا شكرا", "ليس الان", "لا اريد")) {
            return "حسناً، لا مشكلة. أخبرني متى تحتاجني. 💙"
        }

        // ===== "نعم" =====
        if (normalized == "نعم" || matches(normalized, "اجل", "بلا شك", "طبعا")) {
            return "ممتاز! أخبرني ماذا تريد. 🌟"
        }

        // ===== "كيف حالك" =====
        if (matches(normalized, "كيف حالك", "اخبارك", "كيفك", "شلونك")) {
            return "أنا بخير، شكراً! ${context.greeting} كيف حالك أنت؟"
        }

        // ===== "شكرا" =====
        if (matches(normalized, "شكرا", "مشكور", "بارك الله")) {
            return "العفو! 🌟 أنا هنا دائماً."
        }

        // ===== تحيات =====
        if (matches(normalized, "صباح الخير")) {
            return "صباح النور! ☀️ هل تناولت فطورك؟"
        }

        if (matches(normalized, "مساء الخير", "مساء النور")) {
            return "مساء النور! 🌆 كيف كان يومك؟"
        }

        if (matches(normalized, "تصبح على خير", "سنام", "اريد النوم", "طابت ليلتك")) {
            return "طابت ليلتك 🌙 نم جيداً، أراك غداً."
        }

        return null
    }

    /**
     * معالجة "اذا".
     */
    private fun handleIfThen(context: ContextSnapshot): String {
        val sb = StringBuilder()

        if (context.timeSinceLastChat != "الآن" && context.timeSinceLastChat != "أول محادثة") {
            sb.append("أهلاً بعودتك! 👋\n\n")

            if (context.lastTopic.isNotBlank()) {
                sb.append("كنّا نتحدث عن: \"${context.lastTopic.take(50)}\"\n\n")
                sb.append("نُكمل؟ أم لديك شيء آخر؟")
            } else {
                sb.append("كيف يمكنني مساعدتك الآن؟")
            }
        } else {
            if (context.lastTopic.isNotBlank()) {
                sb.append("نحن في موضوع: \"${context.lastTopic.take(50)}\"\n\n")
                sb.append("ماذا تريد أن نفعل؟")
            } else {
                sb.append("ماذا تريد أن نفعل الآن؟ 🌟")
            }
        }

        return sb.toString()
    }

    /**
     * معالجة "لماذا" — بحاجة للسياق.
     */
    private fun handleWhy(context: ContextSnapshot): String {
        return if (context.lastRachaReply.isNotBlank()) {
            "بخصوص ردّي السابق: \"${context.lastRachaReply.take(60)}...\"\n\n" +
            "هل تريد توضيحاً أكثر؟ 🤔"
        } else {
            "لماذا ماذا؟ 🤔\nأخبرني ما الذي تريد فهمه."
        }
    }

    /**
     * معالجة "كيف".
     */
    private fun handleHow(context: ContextSnapshot): String {
        return if (context.lastRachaReply.isNotBlank()) {
            "بخصوص: \"${context.lastRachaReply.take(60)}...\"\n\n" +
            "هل تريد شرحاً أكثر؟ 📖"
        } else {
            "كيف ماذا؟ 🤔\nأخبرني ما الذي تحتاج مساعدة فيه."
        }
    }

    /**
     * معالجة "ماذا".
     */
    private fun handleWhat(context: ContextSnapshot): String {
        return if (context.lastRachaReply.isNotBlank()) {
            "بخصوص: \"${context.lastRachaReply.take(60)}...\"\n\n" +
            "هل تريد توضيحاً؟ 🤔"
        } else {
            "ماذا تريد أن تعرف؟ 🌟"
        }
    }

    /**
     * معالجة "حسنا".
     */
    private fun handleAcknowledgment(): String {
        val options = listOf(
            "ممتاز! ماذا بعد؟ 🌟",
            "جيد! أخبرني المزيد.",
            "تمام. أنا جاهز. 💙",
            "حسناً. ماذا تريد الآن؟"
        )
        return options.random()
    }

    private fun normalize(input: String): String {
        return input
            .lowercase()
            .trim()
            .replace("إ", "ا")
            .replace("أ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace(Regex("[؟?!.,،]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun matches(text: String, vararg keywords: String): Boolean {
        val normalizedText = normalize(text)
        return keywords.any { keyword ->
            normalizedText == normalize(keyword) ||
            normalizedText.contains(normalize(keyword))
        }
    }
}
