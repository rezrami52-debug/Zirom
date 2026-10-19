package com.racha.ai.core.context

object SmartReplies {
    private val GREETINGS = listOf("اهلا", "أهلا", "مرحبا", "السلام", "صباح", "مساء", "hi", "hello", "هاي", "كيف حالك", "كيفك", "اخبارك", "شلونك", "شكرا", "مشكور", "شكراً", "نعم", "لا", "حسنا", "حسن", "تمام", "طيب", "اوكي", "بخير", "الحمد", "بخير وانت")

    fun tryReply(input: String, context: ContextSnapshot): String? {
        val normalized = normalize(input)
        if (matches(normalized, "اذا", "واذا", "ماذا الان", "ماذا بعد", "ثم ماذا")) return handleIfThen(context)
        if (matches(normalized, "حسنا", "حسن", "تمام", "طيب", "اوكي", "ماشي")) return handleAcknowledgment()
        if (normalized == "لماذا" || normalized == "لماذا؟") return handleWhy(context)
        if (normalized == "لا" || matches(normalized, "لا شكرا", "ليس الان")) return "حسناً، لا مشكلة. أخبرني متى تحتاجني. 💙"
        if (normalized == "نعم" || matches(normalized, "اجل", "طبعا")) return "ممتاز! أخبرني ماذا تريد. 🌟"
        if (matches(normalized, "كيف حالك", "اخبارك", "كيفك")) return "أنا بخير، شكراً! ${context.greeting} كيف حالك أنت؟"
        if (matches(normalized, "شكرا", "مشكور")) return "العفو! 🌟 أنا هنا دائماً."
        if (matches(normalized, "صباح الخير")) return "صباح النور! ☀️ هل تناولت فطورك؟"
        if (matches(normalized, "مساء الخير", "مساء النور")) return "مساء النور! 🌆 كيف كان يومك؟"
        if (matches(normalized, "تصبح على خير", "اريد النوم")) return "طابت ليلتك 🌙 نم جيداً، أراك غداً."
        return null
    }

    private fun handleIfThen(context: ContextSnapshot): String {
        val sb = StringBuilder()
        val topicIsGreeting = isGreeting(context.lastTopic)
        if (context.timeSinceLastChat != "الآن" && context.timeSinceLastChat != "أول محادثة") {
            sb.append("أهلاً بعودتك! 👋\n\n")
            if (context.lastTopic.isNotBlank() && !topicIsGreeting) {
                sb.append("كنّا نتحدث عن: \"${context.lastTopic.take(50)}\"\n\n")
                sb.append("نُكمل؟ أم لديك شيء آخر؟")
            } else sb.append("كيف يمكنني مساعدتك الآن؟")
        } else {
            if (context.lastTopic.isNotBlank() && !topicIsGreeting) {
                sb.append("نحن في موضوع: \"${context.lastTopic.take(50)}\"\n\n")
                sb.append("ماذا تريد أن نفعل؟")
            } else sb.append("ماذا تريد أن نفعل الآن؟ 🌟")
        }
        return sb.toString()
    }

    private fun handleWhy(context: ContextSnapshot): String {
        return if (context.lastRachaReply.isNotBlank()) "بخصوص ردّي السابق: \"${context.lastRachaReply.take(60)}...\"\n\nهل تريد توضيحاً أكثر؟ 🤔"
        else "لماذا ماذا؟ 🤔\nأخبرني ما الذي تريد فهمه."
    }

    private fun handleAcknowledgment(): String {
        val options = listOf("ممتاز! ماذا بعد؟ 🌟", "جيد! أخبرني المزيد.", "تمام. أنا جاهز. 💙", "حسناً. ماذا تريد الآن؟")
        return options.random()
    }

    private fun isGreeting(text: String): Boolean {
        if (text.isBlank()) return true
        val normalized = normalize(text)
        return GREETINGS.any { normalize(it) == normalized || normalized.startsWith(normalize(it)) }
    }

    private fun normalize(input: String): String {
        return input.lowercase().trim().replace("إ", "ا").replace("أ", "ا").replace("آ", "ا").replace("ة", "ه").replace("ى", "ي").replace(Regex("[\\u064B-\\u065F\\u0670]"), "").replace(Regex("[؟?!.,،]"), "").replace(Regex("\\s+"), " ").trim()
    }

    private fun matches(text: String, vararg keywords: String): Boolean {
        val normalizedText = normalize(text)
        return keywords.any { keyword -> normalizedText == normalize(keyword) || normalizedText.contains(normalize(keyword)) }
    }
}
