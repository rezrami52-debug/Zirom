package com.racha.ai.core.context

data class ContextSnapshot(
    val currentTime: String,
    val currentDate: String,
    val dayPart: DayPart,
    val greeting: String,
    val timeSinceLastChat: String,
    val lastTopic: String,
    val lastUserMessage: String,
    val lastRachaReply: String = "",
    val userMood: UserMood,
    val dayOfWeek: String,
    val locationContext: String = ""
) {
    fun toPrompt(): String {
        val sb = StringBuilder()
        sb.append("[السياق الحالي]\n")
        sb.append("- الوقت: $currentTime ($dayOfWeek)\n")
        sb.append("- ${greeting}\n")

        if (timeSinceLastChat != "الآن" && timeSinceLastChat != "أول محادثة") {
            sb.append("- آخر محادثة: $timeSinceLastChat\n")
        }

        if (lastTopic.isNotBlank()) {
            sb.append("- آخر موضوع: $lastTopic\n")
        }

        if (lastRachaReply.isNotBlank()) {
            sb.append("- ردّي السابق: $lastRachaReply\n")
        }

        if (locationContext.isNotBlank()) {
            sb.append("- الموقع: $locationContext\n")
        }

        if (userMood != UserMood.NEUTRAL) {
            sb.append("- مزاج المستخدم: ${userMood.arabic}\n")
        }

        return sb.toString()
    }
}

enum class DayPart(val arabic: String) {
    MORNING("صباح"),
    NOON("ظهر"),
    AFTERNOON("بعد الظهر"),
    EVENING("مساء"),
    NIGHT("ليل")
}

enum class UserMood(val arabic: String) {
    NEUTRAL("عادي"),
    HAPPY("سعيد"),
    TIRED("متعب"),
    STRESSED("متوتر"),
    EXCITED("متحمس"),
    CONFUSED("مرتبك")
}
