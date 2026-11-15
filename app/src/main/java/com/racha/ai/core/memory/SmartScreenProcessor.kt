package com.racha.ai.core.memory

object SmartScreenProcessor {

    data class ScreenMemory(
        val title: String,
        val information: String,
        val topic: String,
        val keywords: String
    )

    private val uiNoise = setOf(
        "رجوع",
        "عودة",
        "موافق",
        "إلغاء",
        "حفظ",
        "مشاركة",
        "إرسال",
        "فتح",
        "إغلاق",
        "back",
        "home",
        "menu",
        "ok",
        "cancel",
        "send",
        "share",
        "close",
        "android.widget",
        "android.view"
    )

    fun cleanAndFormat(rawText: String): String {
        if (rawText.isBlank()) return ""

        return rawText
            .replace("\\n\\n", "\n")
            .replace("\\n", "\n")
            .replace("\r", "\n")
            .split("\n")
            .map { line ->
                line.trim().replace(Regex("\\s+"), " ")
            }
            .filter { line ->
                line.length > 3 &&
                    !uiNoise.any { noise ->
                        line.equals(noise, ignoreCase = true)
                    } &&
                    !line.startsWith("android.", ignoreCase = true) &&
                    !line.startsWith("androidx.", ignoreCase = true)
            }
            .distinct()
            .joinToString("\n• ")
            .let { cleaned ->
                if (cleaned.isBlank()) "" else "• $cleaned"
            }
            .take(1800)
    }

    fun containsSensitiveData(text: String): Boolean {
        val patterns = listOf(
            Regex("(?i)AIza[0-9A-Za-z_-]{20,}"),
            Regex("(?i)gsk_[0-9A-Za-z_-]{20,}"),
            Regex("(?i)ghp_[0-9A-Za-z_]{20,}"),
            Regex("(?i)api[_ -]?key\\s*[:=]"),
            Regex("(?i)password\\s*[:=]"),
            Regex("(?i)passwd\\s*[:=]"),
            Regex("(?i)token\\s*[:=]"),
            Regex("(?i)authorization\\s*[:=]"),
            Regex("(?i)bearer\\s+[0-9A-Za-z._-]+")
        )

        return patterns.any { pattern ->
            pattern.containsMatchIn(text)
        }
    }

    fun isWorthAnalyzing(text: String): Boolean {
        val normalized = text
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.length < 25) return false
        if (containsSensitiveData(normalized)) return false

        val meaningfulWords = normalized
            .split(" ")
            .count { word -> word.length > 2 }

        return meaningfulWords >= 4
    }

    fun parseScreenMemory(response: String): ScreenMemory? {
        if (response.isBlank()) return null

        val useful = readField(
            response,
            "مفيد",
            "useful",
            "قابل للتعلم"
        )?.lowercase().orEmpty()

        val isUseful =
            useful.contains("نعم") ||
                useful.contains("yes") ||
                useful.contains("true")

        if (!isUseful) return null

        val title = readField(
            response,
            "العنوان",
            "title"
        ).orEmpty().trim()

        val information = readField(
            response,
            "المعلومة",
            "المعلومات",
            "information",
            "summary",
            "الملخص"
        ).orEmpty().trim()

        val topic = readField(
            response,
            "الموضوع",
            "topic",
            "الفئة"
        ).orEmpty().trim()

        val keywords = readField(
            response,
            "الكلمات",
            "الكلمات المفتاحية",
            "keywords"
        ).orEmpty().trim()

        if (title.isBlank()) return null
        if (information.length < 15) return null

        return ScreenMemory(
            title = title.take(120),
            information = information.take(900),
            topic = topic
                .ifBlank { "معلومات عامة" }
                .take(80),
            keywords = keywords.take(240)
        )
    }

    private fun readField(
        text: String,
        vararg labels: String
    ): String? {
        labels.forEach { label ->
            val pattern = Regex(
                "(?im)^\\s*${Regex.escape(label)}\\s*[:：-]\\s*(.+?)\\s*$"
            )

            val value = pattern
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()

            if (!value.isNullOrBlank()) {
                return value
            }
        }

        return null
    }

    fun isLikelyCodeOrTechnical(text: String): Boolean {
        return text.contains(
            Regex("(?i)fun |val |var |class |import |package |\\{ |\\}")
        )
    }
}
