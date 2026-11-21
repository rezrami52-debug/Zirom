package com.racha.ai.core.tools

object LocalApkCommandMatcher {
    private val positivePhrases = listOf(
        "فكك", "فككي", "فككلي", "فك الملف", "فك هذا الملف", "تفكيك",
        "قم بتفكيك", "قومي بتفكيك", "ابدأ التفكيك", "ابدئي التفكيك",
        "decompile", "decompil", "استخرج الشيفرة", "استخرج الكود",
        "اعرض الملفات", "عرض الملفات", "قائمة الملفات"
    )

    private val negativePhrases = listOf(
        "لا تفكك", "لا تفكي", "لا تقم بتفكيك", "لا تقومي بتفكيك",
        "لا تبدأ التفكيك", "لا تبدئي التفكيك", "لا اريد تفكيك", "ما اريد تفكيك",
        "don't decompile", "do not decompile"
    )

    fun matches(text: String): Boolean {
        val normalized = text.lowercase()
            .replace(Regex("[\\u064B-\\u065F\\u0670ـ]"), "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank() || negativePhrases.any(normalized::contains)) return false
        return positivePhrases.any(normalized::contains)
    }
}
