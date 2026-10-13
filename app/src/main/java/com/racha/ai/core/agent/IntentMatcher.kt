package com.racha.ai.core.agent

object IntentMatcher {

    fun normalize(input: String): String {
        return input
            .lowercase()
            .trim()
            .replace("إ", "ا")
            .replace("أ", "ا")
            .replace("آ", "ا")
            .replace("ٱ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace(Regex("\\bال"), "")
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun containsAll(text: String, vararg keywords: String): Boolean {
        val normalized = normalize(text)
        return keywords.all { keyword ->
            normalized.contains(normalize(keyword))
        }
    }

    fun containsAny(text: String, vararg keywords: String): Boolean {
        val normalized = normalize(text)
        return keywords.any { keyword ->
            normalized.contains(normalize(keyword))
        }
    }

    fun isNegation(text: String): Boolean {
        return containsAny(text, "الغ", "الغي", "اوقف", "ايقاف", "ايقف", "لا ت", "لست", "لا اريد")
    }

    fun isQuestion(text: String): Boolean {
        return containsAny(
            text,
            "هل", "لماذا", "متى", "اين", "كيف",
            "ما", "ماذا", "كم", "?" , "؟"
        )
    }
}
