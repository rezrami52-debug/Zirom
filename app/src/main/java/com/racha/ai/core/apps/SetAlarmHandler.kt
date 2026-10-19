package com.racha.ai.core.apps

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log

class SetAlarmHandler(private val context: Context) {
    companion object { private const val TAG = "RachaSetAlarm" }

    data class AlarmRequest(val hour: Int, val minute: Int = 0, val label: String = "Racha")

    fun parseAlarmRequest(text: String): AlarmRequest? {
        val lower = text.lowercase()
        val hourRegex = Regex("(\\d{1,2})")
        val hourMatch = hourRegex.find(lower)?.value?.toIntOrNull() ?: return null
        var hour = hourMatch
        var minute = 0

        val minuteRegex = Regex("(\\d{1,2})\\s*(دقيقة|دقيقه|min)")
        minuteRegex.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { minute = it }

        val isAfternoon = lower.contains("بعد الزوال") || lower.contains("بعد الظهر") || lower.contains("مساء") || lower.contains("العصر") || lower.contains("المغرب") || lower.contains("العشاء") || lower.contains("pm")
        val isMorning = lower.contains("صباح") || lower.contains("الفجر") || lower.contains("am")
        val isNight = lower.contains("ليل") || lower.contains("منتصف الليل")

        when {
            isAfternoon && hour < 12 -> hour += 12
            isNight && hour < 12 -> hour += 12
            isMorning && hour == 12 -> hour = 0
        }
        if (hour !in 0..23) return null
        if (minute !in 0..59) return null
        return AlarmRequest(hour = hour, minute = minute)
    }

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
            true
        } catch (e: Exception) { Log.e(TAG, "فشل ضبط المنبه", e); false }
    }

    fun formatTime(hour: Int, minute: Int): String {
        return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    }
}
