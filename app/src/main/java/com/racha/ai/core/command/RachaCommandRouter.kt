package com.racha.ai.core.command

object RachaCommandRouter {

    fun classify(input: String): RachaCommand {
        val text = input.trim()
        val lower = text.lowercase()

        if (text.isBlank()) {
            return RachaCommand.Unknown
        }

        if (isNearestPavedRoadRequest(lower)) {
            return RachaCommand.FindNearestPavedRoad
        }

        if (isNavigationRequest(lower)) {
            val destination = extractDestination(text)

            return if (destination.isBlank()) {
                RachaCommand.OpenMap
            } else {
                RachaCommand.Navigate(destination)
            }
        }

        if (isLocationRequest(lower)) {
            return RachaCommand.GetLocation
        }

        if (isBatteryRequest(lower)) {
            return RachaCommand.GetBattery
        }

        if (isTimeRequest(lower)) {
            return RachaCommand.GetTime
        }

        if (isDateRequest(lower)) {
            return RachaCommand.GetDate
        }

        if (isScreenRequest(lower)) {
            return RachaCommand.ReadScreen
        }

        if (isFlashlightRequest(lower)) {
            val enabled = !containsAny(
                lower,
                "اطفئ",
                "اطفي",
                "إطفاء",
                "اغلق",
                "اقفل",
                "off"
            )

            return RachaCommand.ToggleFlashlight(enabled)
        }

        if (isVideoRequest(lower)) {
            return RachaCommand.OpenVideos
        }

        if (isPhotoRequest(lower)) {
            return RachaCommand.OpenPhotos
        }

        if (isOpenAppRequest(lower)) {
            return RachaCommand.OpenApp(extractAppName(text))
        }

        return RachaCommand.AskAI(text)
    }

    private fun isNearestPavedRoadRequest(text: String): Boolean {
        return containsAny(
            text,
            "أقرب طريق معبد",
            "اقرب طريق معبد",
            "طريق معبد",
            "الطريق المعبد",
            "أقرب طريق رئيسي",
            "اقرب طريق رئيسي",
            "طريق رئيسي",
            "الطريق الرئيسي",
            "الخروج من الطريق الفلاحي",
            "اخرجني من الطريق الفلاحي",
            "الخروج من الطريق الفرعي",
            "اخرجني من الطريق الفرعي"
        )
    }

    private fun isNavigationRequest(text: String): Boolean {
        return containsAny(
            text,
            "ملاحة",
            "الملاحة",
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
            "مشيني إلى",
            "امشي إلى",
            "روحي بيا إلى",
            "روح بي إلى",
            "طريق إلى",
            "افتحي الخريطة",
            "افتح الخريطة"
        )
    }

    private fun isLocationRequest(text: String): Boolean {
        return containsAny(
            text,
            "أين أنا",
            "اين انا",
            "أين أنا الآن",
            "اين انا الان",
            "وين أنا",
            "وين انا",
            "موقعي الحالي",
            "مكاني الحالي",
            "أين أقف",
            "اين اقف",
            "where am i",
            "my location"
        )
    }

    private fun isBatteryRequest(text: String): Boolean {
        return containsAny(
            text,
            "البطارية",
            "نسبة الشحن",
            "مستوى الشحن",
            "قداش شحنت",
            "قداش البطارية"
        )
    }

    private fun isTimeRequest(text: String): Boolean {
        return containsAny(
            text,
            "كم الساعة",
            "الساعة الآن",
            "الوقت الآن",
            "شنوة الوقت",
            "وقتاش"
        )
    }

    private fun isDateRequest(text: String): Boolean {
        return containsAny(
            text,
            "ما هو التاريخ",
            "شنوة التاريخ",
            "تاريخ اليوم",
            "اليوم شنو"
        )
    }

    private fun isScreenRequest(text: String): Boolean {
        return containsAny(
            text,
            "ماذا ترى",
            "شوفي الشاشة",
            "شوف الشاشة",
            "اقرئي الشاشة",
            "اقرأ الشاشة",
            "حللي الشاشة"
        )
    }

    private fun isFlashlightRequest(text: String): Boolean {
        return containsAny(
            text,
            "شغلي الفلاش",
            "شغل الفلاش",
            "طفي الفلاش",
            "اطفئ الفلاش",
            "اطفي الفلاش",
            "الكشاف"
        )
    }

    private fun isVideoRequest(text: String): Boolean {
        return containsAny(
            text,
            "افتحي الفيديوهات",
            "افتح الفيديوهات",
            "اعرضي الفيديوهات",
            "اعرض الفيديوهات",
            "اعرضي فيديو",
            "اعرض فيديو",
            "فيديواتي",
            "فيديوهاتي",
            "فيديوهات",
            "فيديو",
            "مقاطع الفيديو",
            "المقاطع",
            "شغل الفيديو",
            "شغلي الفيديو"
        )
    }

    private fun isPhotoRequest(text: String): Boolean {
        return containsAny(
            text,
            "افتحي الصور",
            "افتح الصور",
            "اعرضي الصور",
            "اعرض الصور",
            "اعرضي صوري",
            "اعرض صوري",
            "صوري",
            "صوري",
            "صور",
            "الصور",
            "ألبوم الصور",
            "البوم الصور",
            "معرض الصور",
            "المعرض"
        )
    }

    private fun isOpenAppRequest(text: String): Boolean {
        return containsAny(
            text,
            "افتحي تطبيق",
            "افتح تطبيق",
            "افتحي",
            "افتح",
            "شغلي تطبيق",
            "شغل تطبيق"
        )
    }

    private fun extractDestination(input: String): String {
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
            "مشيني إلى",
            "امشي إلى",
            "روحي بيا إلى",
            "روح بي إلى",
            "طريق إلى"
        )

        for (prefix in prefixes) {
            if (input.startsWith(prefix, ignoreCase = true)) {
                return input
                    .substring(prefix.length)
                    .replace("يا رمزي", "")
                    .replace("رمزي", "")
                    .trim()
            }
        }

        return ""
    }

    private fun extractAppName(input: String): String {
        return input
            .replace(
                Regex(
                    "(?i)افتحي|افتح|شغلي|شغل|تطبيق|برنامج|يا رمزي|رمزي"
                ),
                ""
            )
            .trim()
    }

    private fun containsAny(
        text: String,
        vararg values: String
    ): Boolean {
        return values.any { text.contains(it, ignoreCase = true) }
    }
}
