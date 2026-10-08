package com.racha.ai.services.prayer

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.racha.ai.domain.model.PrayerTimes as RachaPrayerTimes
import java.util.Date

object PrayerTimesCalculator {

    fun calculate(
        latitude: Double,
        longitude: Double,
        date: Date = Date(),
        locationName: String = "موقعك"
    ): RachaPrayerTimes {
        val coordinates = Coordinates(latitude, longitude)
        val dateComponents = DateComponents.from(date)

        // adhan 1.2.0: parameters يحوي Madhab.SHAFI افتراضياً
        val params = CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters

        val times = PrayerTimes(coordinates, dateComponents, params)

        return RachaPrayerTimes(
            fajr = times.fajr,
            sunrise = times.sunrise,
            dhuhr = times.dhuhr,
            asr = times.asr,
            maghrib = times.maghrib,
            isha = times.isha,
            calculationMethod = "رابطة العالم الإسلامي",
            locationName = locationName
        )
    }
}
