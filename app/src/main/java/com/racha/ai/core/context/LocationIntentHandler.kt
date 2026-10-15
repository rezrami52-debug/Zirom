package com.racha.ai.core.context

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.racha.ai.core.agent.IntentMatcher
import com.racha.ai.services.location.LocationHelper

object LocationIntentHandler {

    private const val TAG = "RachaLocation"

    // تطبيقات الخرائط المتاحة
    private const val GOOGLE_EARTH = "com.google.earth"
    private const val GOOGLE_MAPS = "com.google.android.apps.maps"
    private const val OSMAND = "net.osmand"

    /**
     * هل السؤال عن الموقع؟ (نصي)
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
     * هل يريد فتح الخريطة؟
     */
    fun isMapRequest(input: String): Boolean {
        val hasMapKeyword = IntentMatcher.containsAny(
            input,
            "خريطه", "خريطة", "الخريطه", "الخريطة",
            "earth", "ايرث",
            "maps", "map", "خرايط",
            "gps", "جي بي اس"
        )
        val hasActionKeyword = IntentMatcher.containsAny(
            input,
            "اعرض", "افتح", "ارني", "أرني", "ورني",
            "show", "open"
        )
        return hasMapKeyword && hasActionKeyword
    }

    /**
     * إجابة سؤال الموقع (نصية).
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

    /**
     * فتح الموقع على الخريطة.
     * يجرب: Google Earth → Google Maps → أي تطبيق خرائط.
     */
    suspend fun openInMaps(context: Context): String {
        val location = LocationHelper.getCurrentLocation(context)

        if (location == null) {
            return "⚠️ لا أستطيع الوصول لموقعك.\nتأكد من تفعيل GPS."
        }

        val lat = location.latitude
        val lon = location.longitude
        val label = location.cityName ?: "موقعي"

        // === 1. جرب Google Earth ===
        if (isAppInstalled(context, GOOGLE_EARTH)) {
            try {
                // Google Earth يستخدم geo: أو تطبيق مباشر
                val earthIntent = context.packageManager
                    .getLaunchIntentForPackage(GOOGLE_EARTH)
                if (earthIntent != null) {
                    earthIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // إضافة الإحداثيات
                    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
                    earthIntent.data = uri
                    context.startActivity(earthIntent)
                    Log.d(TAG, "✅ فُتح Google Earth")
                    return "🌍 تم فتح موقعك في Google Earth"
                }
            } catch (e: Exception) {
                Log.w(TAG, "فشل Google Earth", e)
            }
        }

        // === 2. جرب Google Maps ===
        if (isAppInstalled(context, GOOGLE_MAPS)) {
            try {
                val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
                val mapsIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(GOOGLE_MAPS)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(mapsIntent)
                Log.d(TAG, "✅ فُتح Google Maps")
                return "🗺️ تم فتح موقعك في Google Maps"
            } catch (e: Exception) {
                Log.w(TAG, "فشل Google Maps", e)
            }
        }

        // === 3. أي تطبيق خرائط ===
        return try {
            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
            val genericIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(genericIntent)
            Log.d(TAG, "✅ فُتح تطبيق خرائط عام")
            "🗺️ تم فتح موقعك على الخريطة"
        } catch (e: Exception) {
            Log.e(TAG, "فشل فتح أي تطبيق خرائط", e)
            "⚠️ لا يوجد تطبيق خرائط على هاتفك.\n\n" +
            "الإحداثيات: ($lat, $lon)\n" +
            "انسخها يدوياً."
        }
    }

    /**
     * هل التطبيق مثبت؟
     */
    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
