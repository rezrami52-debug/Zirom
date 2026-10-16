package com.racha.ai.core.util

import java.util.Calendar

object TimeHelper {
    
    /**
     * يحدد ما إذا كان الوقت الحالي ليلاً (من 6 مساءً حتى 6 صباحاً)
     */
    fun isNightTime(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        // الليل يبدأ من الساعة 18 (6 مساءً) وينتهي عند الساعة 6 صباحاً
        return currentHour >= 18 || currentHour < 6
    }

    /**
     * يحدد ما إذا كان الوقت نهاراً
     */
    fun isDayTime(): Boolean = !isNightTime()

    /**
     * يحصل على الوقت الحالي كنص (مثال: "10:30 مساءً")
     */
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
