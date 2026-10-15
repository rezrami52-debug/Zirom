package com.racha.ai.core.context

import android.content.Context
import com.racha.ai.data.local.ChatMessageEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class ContextBuilder(private val context: Context) {

    fun build(
        lastMessages: List<ChatMessageEntity>,
        currentUserMessage: String,
        locationContext: String = ""
    ): ContextSnapshot {

        val now = Calendar.getInstance()

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = timeFormat.format(now.time)

        val dateFormat = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar"))
        val currentDate = dateFormat.format(now.time)

        val dayOfWeek = SimpleDateFormat("EEEE", Locale("ar")).format(now.time)

        val hour = now.get(Calendar.HOUR_OF_DAY)
        val dayPart = when (hour) {
            in 5..11 -> DayPart.MORNING
            in 12..13 -> DayPart.NOON
            in 14..17 -> DayPart.AFTERNOON
            in 18..21 -> DayPart.EVENING
            else -> DayPart.NIGHT
        }

        val greeting = when (dayPart) {
            DayPart.MORNING -> "صباح الخير ☀️"
            DayPart.NOON -> "نهارك سعيد 🌤️"
            DayPart.AFTERNOON -> "مساء النور 🌆"
            DayPart.EVENING -> "مساء الخير 🌆"
            DayPart.NIGHT -> "طابت ليلتك 🌙"
        }

        val lastMessage = lastMessages.lastOrNull()
        val timeSinceLastChat = if (lastMessage == null) {
            "أول محادثة"
        } else {
            formatTimeGap(System.currentTimeMillis() - lastMessage.timestamp)
        }

        val lastUserMsg = lastMessages.lastOrNull { it.isFromUser }
        val lastUserMessage = lastUserMsg?.text?.take(100) ?: ""

        // آخر رد من Racha (مهم لـ "لماذا")
        val lastRachaMsg = lastMessages.lastOrNull { !it.isFromUser }
        val lastRachaReply = lastRachaMsg?.text?.take(100) ?: ""

        val lastTopic = lastUserMsg?.text?.take(60) ?: ""

        val userMood = detectMood(currentUserMessage)

        return ContextSnapshot(
            currentTime = currentTime,
            currentDate = currentDate,
            dayPart = dayPart,
            greeting = greeting,
            timeSinceLastChat = timeSinceLastChat,
            lastTopic = lastTopic,
            lastUserMessage = lastUserMessage,
            lastRachaReply = lastRachaReply,
            userMood = userMood,
            dayOfWeek = dayOfWeek,
            locationContext = locationContext
        )
    }

    private fun formatTimeGap(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val days = TimeUnit.MILLISECONDS.toDays(millis)
        return when {
            minutes < 1 -> "الآن"
            minutes < 60 -> "منذ $minutes دقيقة"
            hours < 24 -> "منذ $hours ساعة"
            days < 7 -> "منذ $days يوم"
            else -> "منذ $days أيام"
        }
    }

    private fun detectMood(message: String): UserMood {
        val normalized = message.lowercase().trim()
        return when {
            matchesAny(normalized, "متعب", "تعبت", "مرهق", "نعسان") -> UserMood.TIRED
            matchesAny(normalized, "متوتر", "قلق", "خايف", "مشكلة") -> UserMood.STRESSED
            matchesAny(normalized, "متحمس", "رائع", "ممتاز", "أخيراً") -> UserMood.EXCITED
            matchesAny(normalized, "سعيد", "فرحان", "الحمد", "بخير") -> UserMood.HAPPY
            matchesAny(normalized, "مرتبك", "محتار", "ما أعرف") -> UserMood.CONFUSED
            else -> UserMood.NEUTRAL
        }
    }

    private fun matchesAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }
}
