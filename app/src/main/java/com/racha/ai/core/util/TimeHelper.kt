package com.racha.ai.core.util

import java.util.Calendar

object TimeHelper {
    
    fun isNightTime(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return currentHour >= 18 || currentHour < 6
    }

    fun isDayTime(): Boolean = !isNightTime()

    fun getCurrentTimeString(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val isAm = hour < 12
        
        val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val period = if (isAm) "صباحاً" else "مساءً"
        
        return String.format("%02d:%02d %s", displayHour, minute, period)
    }
}
