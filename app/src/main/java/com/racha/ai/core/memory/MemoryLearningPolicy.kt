package com.racha.ai.core.memory

object MemoryLearningPolicy {

    fun isLearningTrigger(text: String): Boolean {
        val normalized = normalize(text)

        return normalized == "وقت التعلم الان" ||
            normalized == "وقت التعلم" ||
            normalized == "تعلمي"
    }

    fun cleanForMemory(text: String, maxLength: Int = 1400): String {
        if (text.isBlank() || isSensitive(text)) return ""

        return text
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLength)
    }

    fun isSensitive(text: String): Boolean {
        if (text.isBlank()) return true

        val patterns = listOf(
            Regex("(?i)AIza[0-9A-Za-z_-]{20,}"),
            Regex("(?i)gsk_[0-9A-Za-z_-]{20,}"),
            Regex("(?i)ghp_[0-9A-Za-z_]{20,}"),
            Regex("(?i)github_pat_[0-9A-Za-z_]{20,}"),
            Regex("(?i)bearer\\s+[0-9A-Za-z._-]{20,}"),
            Regex("(?i)(api[_-]?key|token|password|passwd|secret)\\s*[:=]\\s*\\S+"),
            Regex("\\b\\d{13,19}\\b")
        )

        if (patterns.any { it.containsMatchIn(text) }) return true

        val lower = text.lowercase()

        return listOf(
            "كلمة المرور",
            "كلمة السر",
            "رمز التحقق",
            "رمز الدخول",
            "مفتاح api",
            "api key",
            "access token",
            "password",
            "passwd",
            "secret",
            "token"
        ).any { lower.contains(it.lowercase()) }
    }

    private fun normalize(text: String): String {
        return text
            .trim()
            .lowercase()
            .replace(Regex("[ًٌٍَُِّْـ]"), "")
            .replace(Regex("[؟?!.,،؛:]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
