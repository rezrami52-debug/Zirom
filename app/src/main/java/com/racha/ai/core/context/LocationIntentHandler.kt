package com.racha.ai.core.context

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.racha.ai.core.agent.IntentMatcher
import com.racha.ai.services.location.LocationHelper
import java.util.Locale

object LocationIntentHandler {

    private const val TAG = "RachaLocation"

    private const val GOOGLE_EARTH = "com.google.earth"
    private const val GOOGLE_MAPS = "com.google.android.apps.maps"
    private const val OSMAND = "net.osmand"

    fun isLocationQuestion(input: String): Boolean {
        return IntentMatcher.containsAny(
            input,
            "اين انا",
            "أين أنا",
            "اين انا الان",
            "أين أنا الآن",
            "وين انا",
            "وين أنا",
            "موقعي",
            "مكاني",
            "اين اقف",
            "أين أقف",
            "موقعي الحالي",
            "مكاني الحالي",
            "where am i",
            "my location"
        )
    }

    fun isMapRequest(input: String): Boolean {
        val hasMapKeyword = IntentMatcher.containsAny(
            input,
            "خريطه",
            "خريطة",
            "الخريطه",
            "الخريطة",
            "earth",
            "ايرث",
            "maps",
            "map",
            "خرايط",
            "gps",
            "جي بي اس"
        )

        val hasActionKeyword = IntentMatcher.containsAny(
            input,
            "اعرض",
            "أعرض",
            "افتح",
            "افتحي",
            "ارني",
            "أرني",
            "ورني",
            "show",
            "open"
        )

        return hasMapKeyword && hasActionKeyword
    }

    suspend fun answer(context: Context): String {
        val location = LocationHelper.getCurrentLocation(context)

        if (location == null) {
            return "⚠️ لا أستطيع الوصول إلى موقعك.\n\n" +
                "تأكد من:\n" +
                "1. تفعيل GPS\n" +
                "2. منح Racha صلاحية الموقع\n" +
                "3. الوقوف في مكان مفتوح لبضع ثوانٍ"
        }

        val lat = String.format(
            Locale.US,
            "%.6f",
            location.latitude
        )

        val lon = String.format(
            Locale.US,
            "%.6f",
            location.longitude
        )

        val lines = mutableListOf<String>()

        lines.add("📍 تفاصيل موقعك الحالي:")

        location.fullAddress
            ?.takeIf { it.isNotBlank() }
            ?.let { lines.add("العنوان: $it") }

        location.street
            ?.takeIf { it.isNotBlank() }
            ?.let { street ->
                val building = location.buildingNumber
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "، رقم المبنى $it" }
                    ?: ""

                lines.add("الشارع: $street$building")
            }

        location.neighborhood
            ?.takeIf { it.isNotBlank() }
            ?.let { lines.add("الحي أو المنطقة: $it") }

        location.delegation
            ?.takeIf { it.isNotBlank() }
            ?.let { lines.add("المعتمدية: $it") }

        location.cityName
            ?.takeIf { it.isNotBlank() }
            ?.let { lines.add("المدينة: $it") }

        location.governorate
            ?.takeIf { it.isNotBlank() }
            ?.let { lines.add("الولاية: $it") }

        location.postalCode
            ?.takeIf { it.isNotBlank() }
            ?.let { lines.add("الرمز البريدي: $it") }

        location.countryName
            ?.takeIf { it.isNotBlank() }
            ?.let { lines.add("الدولة: $it") }

        lines.add("")
        lines.add("الإحداثيات:")
        lines.add("خط العرض: $lat")
        lines.add("خط الطول: $lon")

        if (
            location.fullAddress.isNullOrBlank() &&
            location.street.isNullOrBlank() &&
            location.neighborhood.isNullOrBlank()
        ) {
            lines.add("")
            lines.add(
                "لم يتمكن نظام العنوان من تحديد الشارع أو الحي بدقة."
            )
        }

        lines.add("")
        lines.add(
            "ملاحظة: تحديد اسم مقهى أو مكان داخل مبنى يحتاج " +
                "خدمة بحث عن الأماكن القريبة، ولا يمكن تأكيده من GPS وحده."
        )

        return lines.joinToString("\n")
    }

    suspend fun openInMaps(context: Context): String {
        val location = LocationHelper.getCurrentLocation(context)

        if (location == null) {
            return "⚠️ لا أستطيع الوصول إلى موقعك.\n" +
                "تأكد من تفعيل GPS ومنح Racha صلاحية الموقع."
        }

        val lat = location.latitude
        val lon = location.longitude
        val label = location.fullAddress
            ?: location.cityName
            ?: "موقعي"

        if (isAppInstalled(context, GOOGLE_EARTH)) {
            try {
                val earthIntent = context.packageManager
                    .getLaunchIntentForPackage(GOOGLE_EARTH)

                if (earthIntent != null) {
                    earthIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    val uri = Uri.parse(
                        "geo:$lat,$lon?q=$lat,$lon($label)"
                    )

                    earthIntent.data = uri
                    context.startActivity(earthIntent)

                    Log.d(TAG, "✅ فُتح Google Earth")
                    return "🌍 تم فتح موقعك في Google Earth"
                }
            } catch (e: Exception) {
                Log.w(TAG, "فشل Google Earth", e)
            }
        }

        if (isAppInstalled(context, GOOGLE_MAPS)) {
            try {
                val uri = Uri.parse(
                    "geo:$lat,$lon?q=$lat,$lon($label)"
                )

                val mapsIntent = Intent(
                    Intent.ACTION_VIEW,
                    uri
                ).apply {
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

        return try {
            val uri = Uri.parse(
                "geo:$lat,$lon?q=$lat,$lon($label)"
            )

            val genericIntent = Intent(
                Intent.ACTION_VIEW,
                uri
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(genericIntent)

            Log.d(TAG, "✅ فُتح تطبيق خرائط عام")
            "🗺️ تم فتح موقعك على الخريطة"
        } catch (e: Exception) {
            Log.e(TAG, "فشل فتح أي تطبيق خرائط", e)

            val formattedLat = String.format(
                Locale.US,
                "%.6f",
                lat
            )

            val formattedLon = String.format(
                Locale.US,
                "%.6f",
                lon
            )

            "⚠️ لا يوجد تطبيق خرائط على هاتفك.\n\n" +
                "خط العرض: $formattedLat\n" +
                "خط الطول: $formattedLon"
        }
    }

    private fun isAppInstalled(
        context: Context,
        packageName: String
    ): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
