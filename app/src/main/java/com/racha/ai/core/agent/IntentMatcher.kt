package com.racha.ai.core.agent

/**
 * يطابق النوايا من نص عربي.
 * يعالج: إزالة "ال"، توحيد الهمزات، التطبيع.
 */
object IntentMatcher {

    /**
     * تطبيع النص العربي.
     */
    fun normalize(input: String): String {
        return input
            .lowercase()
            .trim()
            // توحيد الهمزات
            .replace("إ", "ا")
            .replace("أ", "ا")
            .replace("آ", "ا")
            .replace("ٱ", "ا")
            // توحيد التاء المربوطة
            .replace("ة", "ه")
            // توحيد الياء
            .replace("ى", "ي")
            // إزالة "ال" التعريف في بداية الكلمات
            .replace(Regex("\\bال"), "")
            // إزالة التشكيل
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            // مسافات متعددة
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * هل النص يحتوي على كل الكلمات؟
     */
    fun containsAll(text: String, vararg keywords: String): Boolean {
        val normalized = normalize(text)
        return keywords.all { keyword ->
            normalized.contains(normalize(keyword))
        }
    }

    /**
     * هل النص يحتوي على أي كلمة؟
     */
    fun containsAny(text: String, vararg keywords: String): Boolean {
        val normalized = normalize(text)
        return keywords.any { keyword ->
            normalized.contains(normalize(keyword))
        }
    }

    /**
     * هل النص أمر "إلغاء"؟ (نفي)
     */
    fun isNegation(text: String): Boolean {
        return containsAny(text, "الغ", "الغي", "اوقف", "ايقاف", "ايقف", "لا", "وقف")
    }

    /**
     * هل النص سؤال؟
     */
    fun isQuestion(text: String): Boolean {
        return containsAny(text, "هل", "لماذا", "متى", "اين", "كيف", "ما", "ماذا", "?", "؟")
    }
}
