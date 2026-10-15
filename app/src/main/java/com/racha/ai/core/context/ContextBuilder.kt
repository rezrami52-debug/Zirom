package com.racha.ai.core.context

import android.content.Context
import com.racha.ai.data.local.ChatMessageEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * يبني ContextSnapshot من قاعدة البيانات والوقت الحالي.
 */
class ContextBuilder(private val context: Context) {

    /**
     * بناء لقطة سياقية الآن.
     */
    fun build(
        lastMessages: List<ChatMessageEntity>,
        currentUserMessage: String
    ): ContextSnapshot {

        val now = Calendar.getInstance()

        // الوقت
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = timeFormat.format(now.time)

        // التاريخ
        val dateFormat = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar"))
        val currentDate = dateFormat.format(now.time)

        // اليوم من الأسبوع
        val dayOfWeek = SimpleDateFormat("EEEE", Locale("ar")).format(now.time)

        // جزء اليوم
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val dayPart = when (hour) {
            in 5..11 -> DayPart.MORNING
            in 12..13 -> DayPart.NOON
            in 14..17 -> DayPart.AFTERNOON
            in 18..21 -> DayPart.EVENING
            else -> DayPart.NIGHT
        }

        // التحية
        val greeting = when (dayPart) {
            DayPart.MORNING -> "صباح الخير ☀️"
            DayPart.NOON -> "نهارك سعيد 🌤️"
            DayPart.AFTERNOON -> "مساء النور 🌆"
            DayPart.EVENING -> "مساء الخير 🌆"
            DayPart.NIGHT -> "طابت ليلتك 🌙"
        }

        // آخر محادثة
        val lastMessage = lastMessages.lastOrNull()
        val timeSinceLastChat = if (lastMessage == null) {
            "أول محادثة"
        } else {
            formatTimeGap(System.currentTimeMillis() - lastMessage.timestamp)
        }

        // آخر موضوع (من آخر رسالة مستخدم)
        val lastUserMsg = lastMessages.lastOrNull { it.isFromUser }
        val lastUserMessage = lastUserMsg?.text?.take(100) ?: ""

        // آخر موضوع (نستخدم آخر رسالة كنقطة مرجعية)
        val lastTopic = lastUserMsg?.text?.take(60) ?: ""

        // مزاج المستخدم
        val userMood = detectMood(currentUserMessage)

        return ContextSnapshot(
            currentTime = currentTime,
            currentDate = currentDate,
            dayPart = dayPart,
            greeting = greeting,
            timeSinceLastChat = timeSinceLastChat,
            lastTopic = lastTopic,
            lastUserMessage = lastUserMessage,
            userMood = userMood,
            dayOfWeek = dayOfWeek
        )
    }

    /**
     * تنسيق الفجوة الزمنية.
     */
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

    /**
     * كشف مزاج المستخدم من الرسالة.
     */
    private fun detectMood(message: String): UserMood {
        val normalized = message.lowercase().trim()

        return when {
            // متعب
            matchesAny(normalized, "متعب", "تعبت", "مرهق", "نعسان", "أريد النوم") ->
                UserMood.TIRED

            // متوتر
            matchesAny(normalized, "متوتر", "قلق", "خايف", "خائف", "مشكلة", "صعب") ->
                UserMood.STRESSED

            // متحمس
            matchesAny(normalized, "متحمس", "رائع", "ممتاز", "أخيراً", "نجحت") ->
                UserMood.EXCITED

            // سعيد
            matchesAny(normalized, "سعيد", "فرحان", "الحمد", "بخير", "تمام") ->
                UserMood.HAPPY

            // مرتبك
            matchesAny(normalized, "مرتبك", "محتار", "ما أعرف", "لا أفهم") ->
                UserMood.CONFUSED

            else -> UserMood.NEUTRAL
        }
    }

    private fun matchesAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }
}
