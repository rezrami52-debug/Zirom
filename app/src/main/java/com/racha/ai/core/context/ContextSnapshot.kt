package com.racha.ai.core.context

/**
 * لقطة سياقية للحظة معينة.
 * تُبنى قبل كل رد، وتُرسل لـ Qwen.
 */
data class ContextSnapshot(
    val currentTime: String,           // "15:51"
    val currentDate: String,           // "الأربعاء، 15 أكتوبر 2026"
    val dayPart: DayPart,              // صباح/ظهر/مساء/ليل
    val greeting: String,              // "مساء الخير" حسب الوقت
    val timeSinceLastChat: String,     // "منذ 18 ساعة" أو "الآن"
    val lastTopic: String,             // "إصلاح البحث"
    val lastUserMessage: String,       // آخر رسالة من المستخدم
    val userMood: UserMood,            // عادي/متحمس/متعب/...
    val dayOfWeek: String              // "الأربعاء"
) {
    /**
     * بناء Prompt نصي للحقن في System Prompt.
     */
    fun toPrompt(): String {
        val sb = StringBuilder()
        sb.append("[السياق الحالي]\n")
        sb.append("- الوقت: $currentTime ($dayOfWeek)\n")
        sb.append("- التاريخ: $currentDate\n")
        sb.append("- الجزء: ${dayPart.arabic}\n")
        sb.append("- ${greeting}\n")

        if (timeSinceLastChat != "الآن") {
            sb.append("- آخر محادثة: $timeSinceLastChat\n")
        }

        if (lastTopic.isNotBlank() && lastTopic != lastUserMessage) {
            sb.append("- آخر موضوع: $lastTopic\n")
        }

        if (userMood != UserMood.NEUTRAL) {
            sb.append("- مزاج المستخدم: ${userMood.arabic}\n")
        }

        return sb.toString()
    }
}

/**
 * جزء اليوم.
 */
enum class DayPart(val arabic: String) {
    MORNING("صباح"),
    NOON("ظهر"),
    AFTERNOON("بعد الظهر"),
    EVENING("مساء"),
    NIGHT("ليل")
}

/**
 * مزاج المستخدم.
 */
enum class UserMood(val arabic: String) {
    NEUTRAL("عادي"),
    HAPPY("سعيد"),
    TIRED("متعب"),
    STRESSED("متوتر"),
    EXCITED("متحمس"),
    CONFUSED("مرتبك")
}
