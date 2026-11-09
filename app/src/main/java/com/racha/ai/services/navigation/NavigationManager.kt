package com.racha.ai.services.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.racha.ai.services.location.LocationHelper
import java.net.URLEncoder

object NavigationManager {

    private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    fun isNavigationRequest(text: String): Boolean {
        val lower = text.lowercase()

        val navigationWords = listOf(
            "ملاحة",
            "الملاحة",
            "خذيني",
            "خذني",
            "أوصليني",
            "أوصلني",
            "دليني",
            "دلني",
            "وجهيني",
            "وجهني",
            "روحي بيا",
            "روح بي",
            "طريق إلى",
            "طريق ل",
            "اذهب إلى",
            "إذهب إلى",
            "امشي إلى",
            "مشيني إلى",
            "أقرب طريق",
            "الطريق المعبد",
            "طريق معبد",
            "طريق رئيسي",
            "طريق عام",
            "طريق فلاحي",
            "طريق فرعي"
        )

        return navigationWords.any { lower.contains(it) }
    }

    suspend fun handleRequest(
        context: Context,
        text: String
    ): String? {
        if (!isNavigationRequest(text)) return null

        return if (isNearestRoadRequest(text)) {
            openNearestMainRoad(context)
        } else {
            val destination = extractDestination(text)

            if (destination.isBlank()) {
                "إلى أين تريد أن أوجهك يا رمزي؟"
            } else {
                openNavigationTo(context, destination)
            }
        }
    }

    private fun isNearestRoadRequest(text: String): Boolean {
        val lower = text.lowercase()

        return lower.contains("أقرب طريق") ||
            lower.contains("اقرب طريق") ||
            lower.contains("الطريق المعبد") ||
            lower.contains("طريق معبد") ||
            lower.contains("الطريق الرئيسي") ||
            lower.contains("طريق رئيسي") ||
            lower.contains("الخروج من الطريق الفلاحي") ||
            lower.contains("الخروج من الطريق الفرعي") ||
            lower.contains("اخرجني من الطريق")
    }

    private fun extractDestination(text: String): String {
        var result = text.trim()

        val prefixes = listOf(
            "خذيني إلى",
            "خذني إلى",
            "أوصليني إلى",
            "أوصلني إلى",
            "دليني إلى",
            "دلني إلى",
            "وجهيني إلى",
            "وجهني إلى",
            "اذهب إلى",
            "إذهب إلى",
            "امشي إلى",
            "مشيني إلى",
            "روحي بيا إلى",
            "روح بي إلى",
            "ملاحة إلى",
            "الملاحة إلى",
            "طريق إلى",
            "طريق ل"
        )

        for (prefix in prefixes) {
            if (result.startsWith(prefix, ignoreCase = true)) {
                result = result.removePrefixIgnoreCase(prefix).trim()
                break
            }
        }

        result = result
            .replace(
                Regex(
                    "(?i)^(افتحي|افتح|شغلي|شغل|ابدئي|ابدأ)\\s+(الملاحة|الخريطة|الخارطة)\\s*"
                ),
                ""
            )
            .trim()

        return result
            .removeSuffix("يا رمزي")
            .removeSuffix("رمزي")
            .trim()
    }

    private suspend fun openNearestMainRoad(context: Context): String {
        val location = LocationHelper.getCurrentLocation(context)

        val query = URLEncoder.encode(
            "أقرب طريق معبد أو طريق رئيسي",
            "UTF-8"
        )

        val uri = if (location != null) {
            Uri.parse(
                "geo:${location.latitude},${location.longitude}?q=$query"
            )
        } else {
            Uri.parse("geo:0,0?q=$query")
        }

        return openMapIntent(
            context = context,
            uri = uri,
            successMessage = "فتحت لك البحث عن أقرب طريق معبد أو رئيسي يا رمزي. تحقق من حالة الطريق قبل العبور."
        )
    }

    private fun openNavigationTo(
        context: Context,
        destination: String
    ): String {
        val encodedDestination = Uri.encode(destination)

        val navigationUri = Uri.parse(
            "google.navigation:q=$encodedDestination&mode=d"
        )

        return try {
            val mapsIntent = Intent(Intent.ACTION_VIEW, navigationUri).apply {
                setPackage(GOOGLE_MAPS_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(mapsIntent)
            "بدأت الملاحة إلى $destination يا رمزي."
        } catch (_: Exception) {
            val webUri = Uri.parse(
                "https://www.google.com/maps/dir/?api=1&destination=$encodedDestination&travelmode=driving"
            )

            try {
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(webIntent)
                "فتحت لك طريق الملاحة إلى $destination يا رمزي."
            } catch (_: Exception) {
                "تعذر فتح تطبيق الخرائط. تأكد من تثبيت Google Maps."
            }
        }
    }

    private fun openMapIntent(
        context: Context,
        uri: Uri,
        successMessage: String
    ): String {
        return try {
            val mapsIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(GOOGLE_MAPS_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(mapsIntent)
            successMessage
        } catch (_: Exception) {
            try {
                val genericIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(genericIntent)
                successMessage
            } catch (_: Exception) {
                "تعذر فتح تطبيق الخرائط على هاتفك."
            }
        }
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String {
        return if (startsWith(prefix, ignoreCase = true)) {
            substring(prefix.length)
        } else {
            this
        }
    }
}
