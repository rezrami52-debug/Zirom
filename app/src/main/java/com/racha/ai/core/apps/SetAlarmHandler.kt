package com.racha.ai.core.apps

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log

/**
 * يتعامل مع طلبات ضبط المنبه.
 */
class SetAlarmHandler(private val context: Context) {

    companion object {
        private const val TAG = "RachaSetAlarm"
    }

    data class AlarmRequest(
        val hour: Int,
        val minute: Int = 0,
        val label: String = "Racha"
    )

    /**
     * استخراج وقت المنبه من النص.
     */
    fun parseAlarmRequest(text: String): AlarmRequest? {
        val lower = text.lowercase()

        // 1. استخراج الساعة (أرقام)
        val hourRegex = Regex("(\\d{1,2})")
        val hourMatch = hourRegex.find(lower)?.value?.toIntOrNull() ?: return null

        var hour = hourMatch
        var minute = 0

        // 2. استخراج الدقائق (إن وُجدت)
        val minuteRegex = Regex("(\\d{1,2})\\s*(دقيقة|دقيقه|min)")
        val minuteMatch = minuteRegex.find(lower)?.groupValues?.get(1)?.toIntOrNull()
        if (minuteMatch != null) {
            minute = minuteMatch
        }

        // 3. تحديد الفترة (صباح / مساء / زوال)
        val isAfternoon = lower.contains("بعد الزوال") ||
                          lower.contains("بعد الظهر") ||
                          lower.contains("مساء") ||
                          lower.contains("العصر") ||
                          lower.contains("المغرب") ||
                          lower.contains("العشاء") ||
                          lower.contains("pm")

        val isMorning = lower.contains("صباح") ||
                        lower.contains("الفجر") ||
                        lower.contains("am")

        val isNight = lower.contains("ليل") ||
                      lower.contains("منتصف الليل")

        // 4. تعديل الساعة
        when {
            isAfternoon && hour < 12 -> hour += 12
            isNight && hour < 12 -> hour += 12
            isMorning && hour == 12 -> hour = 0
        }

        // 5. حدود معقولة
        if (hour !in 0..23) return null
        if (minute !in 0..59) return null

        return AlarmRequest(hour = hour, minute = minute)
    }

    /**
     * ضبط المنبه.
     */
    fun setAlarm(request: AlarmRequest): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, request.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, request.minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, request.label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "✅ المنبه: ${request.hour}:${request.minute}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "فشل ضبط المنبه", e)
            false
        }
    }

    /**
     * تنسيق الوقت للعرض.
     */
    fun formatTime(hour: Int, minute: Int): String {
        val h = hour.toString().padStart(2, '0')
        val m = minute.toString().padStart(2, '0')
        return "$h:$m"
    }
}
