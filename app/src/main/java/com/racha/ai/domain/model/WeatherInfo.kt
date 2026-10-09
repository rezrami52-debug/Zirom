package com.racha.ai.domain.model

data class WeatherInfo(
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windSpeed: Double,
    val weatherCode: Int,
    val cityName: String = ""
) {
    /**
     * ترجمة رمز الطقس (WMO code) إلى وصف عربي.
     * المرجع: https://open-meteo.com/en/docs
     */
    fun description(): String = when (weatherCode) {
        0 -> "صافٍ"
        1 -> "صافٍ غالباً"
        2 -> "غائم جزئياً"
        3 -> "غائم"
        45, 48 -> "ضباب"
        51, 53, 55 -> "رذاذ خفيف"
        56, 57 -> "رذاذ متجمد"
        61 -> "مطر خفيف"
        63 -> "مطر متوسط"
        65 -> "مطر غزير"
        66, 67 -> "مطر متجمد"
        71 -> "ثلج خفيف"
        73 -> "ثلج متوسط"
        75 -> "ثلج كثيف"
        77 -> "حبيبات ثلجية"
        80 -> "زخات مطر خفيفة"
        81 -> "زخات مطر متوسطة"
        82 -> "زخات مطر عنيفة"
        85, 86 -> "زخات ثلجية"
        95 -> "عاصفة رعدية"
        96, 99 -> "عاصفة رعدية مع برد"
        else -> "غير معروف"
    }

    /**
     * وصف عربي كامل للإشعار.
     */
    fun fullArabicDescription(): String {
        val temp = temperature.toInt()
        val feels = feelsLike.toInt()
        val desc = description()

        val emoji = when (weatherCode) {
            0, 1 -> "☀️"
            2 -> "⛅"
            3 -> "☁️"
            45, 48 -> "🌫️"
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
            71, 73, 75, 77, 85, 86 -> "❄️"
            95, 96, 99 -> "⛈️"
            else -> "🌡️"
        }

        val base = "$emoji الطقس الآن: $desc، الحرارة $temp° (تُحس كـ $feels°)"
        val extra = if (windSpeed > 20) "، رياح قوية (${windSpeed.toInt()} كم/س)" else ""
        return base + extra
    }
}
