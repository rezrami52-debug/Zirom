package com.racha.ai.core.context

import android.content.Context
import com.racha.ai.core.agent.IntentMatcher
import com.racha.ai.services.location.LocationHelper

/**
 * يتعامل مع أسئلة الموقع.
 */
object LocationIntentHandler {

    /**
     * هل السؤال عن الموقع؟
     */
    fun isLocationQuestion(input: String): Boolean {
        return IntentMatcher.containsAny(
            input,
            "اين انا", "اين انا الان", "وين انا",
            "موقعي", "مكاني", "اين اقف",
            "where am i", "my location"
        )
    }

    /**
     * إجابة سؤال الموقع.
     */
    suspend fun answer(context: Context): String {
        val location = LocationHelper.getCurrentLocation(context)

        return if (location == null) {
            "⚠️ لا أستطيع الوصول لموقعك.\n\nتأكد من:\n" +
            "1. تفعيل GPS في الهاتف\n" +
            "2. منح Racha صلاحية الموقع"
        } else {
            val city = location.cityName ?: "منطقتك الحالية"
            val lat = String.format("%.4f", location.latitude)
            val lon = String.format("%.4f", location.longitude)
            "📍 أنت في: $city\n($lat, $lon)"
        }
    }
}
