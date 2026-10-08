package com.racha.ai.domain.model

import java.util.Date

data class PrayerTimes(
    val fajr: Date,
    val sunrise: Date,
    val dhuhr: Date,
    val asr: Date,
    val maghrib: Date,
    val isha: Date,
    val calculationMethod: String,
    val locationName: String
) {
    fun asMap(): Map<String, Date> = mapOf(
        "الفجر" to fajr,
        "الشروق" to sunrise,
        "الظهر" to dhuhr,
        "العصر" to asr,
        "المغرب" to maghrib,
        "العشاء" to isha
    )

    fun nextPrayer(now: Date = Date()): Pair<String, Date>? {
        val upcoming = asMap()
            .filter { (_, time) -> time.after(now) }
            .minByOrNull { (_, time) -> time.time }
        return upcoming?.toPair()
    }
}
