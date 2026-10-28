package com.racha.ai.core.apps

object IntentClassifier {

    enum class IntentType {
        SHOW_LOCATION, OPEN_MAP, SEND_MESSAGE, MAKE_CALL,
        SEND_EMAIL, OPEN_BROWSER, OPEN_CAMERA, PLAY_MUSIC,
        OPEN_APP, LIST_APPS, SET_ALARM, TOGGLE_WIFI, SET_RINGER,
        GET_TIME, GET_DATE, GET_BATTERY, TOGGLE_FLASHLIGHT,
        TOGGLE_BLUETOOTH, GREETING, SMALL_TALK, PRAYER_TIMES,
        FIND_MY_PHONE, MOSQUE_MODE, MACRO_SLEEP, MACRO_CINEMA, MACRO_WORK,
        OPEN_PHOTOS, OPEN_VIDEOS, OPEN_MUSIC, OPEN_NOTIFICATIONS,
        CORRECTION, UNKNOWN
    }

    fun classify(text: String): IntentType {
        val lower = text.lowercase().trim()

        if (isCorrection(text)) return IntentType.CORRECTION
        if (isSetAlarm(text)) return IntentType.SET_ALARM

        // أوامر عرض واستعراض الإشعارات
        if (lower.contains("اشعار") || lower.contains("اشعارات") || lower.contains("إشعار") || lower.contains("إشعارات")) {
            if (lower.contains("اعرض") || lower.contains("اعرضي") || lower.contains("افتح") || lower.contains("افتحي") ||
                lower.contains("استعرض") || lower.contains("استعرضي") || lower.contains("شوفي") || lower.contains("وريني") || lower.contains("هاتي")) {
                return IntentType.OPEN_NOTIFICATIONS
            }
        }

        // أوامر الوسائط
        val isMediaCmd = lower.contains("استعرض") || lower.contains("استعرضي") || lower.contains("اعرض") ||
                         lower.contains("اعرضي") || lower.contains("وريني") || lower.contains("أرني") ||
                         lower.contains("ارني") || lower.contains("شوفي") || lower.contains("شوف") ||
                         lower.contains("هاتي") || lower.contains("هات")

        if (isMediaCmd && (lower.contains("فيديو") || lower.contains("فيديوهات") || lower.contains("مقاطع") || lower.contains("مقطع"))) return IntentType.OPEN_VIDEOS
        if (isMediaCmd && (lower.contains("صور") || lower.contains("صورة") || lower.contains("صوري") || lower.contains("البوم") || lower.contains("ألبوم") || lower.contains("معرض"))) return IntentType.OPEN_PHOTOS
        if (isMediaCmd && (lower.contains("موسيقى") || lower.contains("اغاني") || lower.contains("أغاني") || lower.contains("صوتيات") || lower.contains("غناء"))) return IntentType.OPEN_MUSIC

        // أوامر فتح التطبيقات
        val isOpenAppCmd = lower.startsWith("افتح") || lower.startsWith("افتحي") || lower.startsWith("شغل") ||
                           lower.startsWith("شغلي") || lower.startsWith("حل") || lower.startsWith("حلي") ||
                           lower.contains("تطبيق") || lower.contains("برنامج")

        if (isOpenAppCmd && !lower.contains("كاميرا") && !lower.contains("متصفح") && !lower.contains("خريطة") && !lower.contains("اشعار")) {
            return IntentType.OPEN_APP
        }

        if (isFindPhoneQuery(lower)) return IntentType.FIND_MY_PHONE

        if (lower.contains("وضع المسجد") || lower.contains("صلاة المسجد")) return IntentType.MOSQUE_MODE
        if (lower.contains("وضع النوم") || lower.contains("تصبح على خير") || lower.contains("نرقد")) return IntentType.MACRO_SLEEP
        if (lower.contains("وضع السينما") || lower.contains("وضع الفيلم")) return IntentType.MACRO_CINEMA
        if (lower.contains("وضع العمل") || lower.contains("وضع التركيز")) return IntentType.MACRO_WORK

        if (isTimeQuery(lower)) return IntentType.GET_TIME
        if (isDateQuery(lower)) return IntentType.GET_DATE
        if (isBatteryQuery(lower)) return IntentType.GET_BATTERY
        if (isFlashlightQuery(lower)) return IntentType.TOGGLE_FLASHLIGHT
        if (isBluetoothQuery(lower)) return IntentType.TOGGLE_BLUETOOTH
        if (isGreeting(lower)) return IntentType.GREETING
        if (isPrayerQuery(lower)) return IntentType.PRAYER_TIMES
        if (isSmallTalk(lower)) return IntentType.SMALL_TALK

        if (lower.contains("تطبيق") && (lower.contains("قائمة") || lower.contains("كل"))) return IntentType.LIST_APPS
        if (lower.contains("اين") && lower.contains("انا")) return IntentType.SHOW_LOCATION
        if (lower.contains("خريطة") || lower.contains("موقعي")) return IntentType.OPEN_MAP
        if (lower.contains("رسالة") && (lower.contains("ارسل") || lower.contains("ابعث"))) return IntentType.SEND_MESSAGE
        if (lower.contains("اتصل") || lower.contains("اتصال") || lower.contains("call")) return IntentType.MAKE_CALL
        if (lower.contains("كاميرا")) return IntentType.OPEN_CAMERA
        if (lower.contains("واي فاي") || lower.contains("wifi") || lower.contains("ويفي")) return IntentType.TOGGLE_WIFI
        if (lower.contains("صامت") || lower.contains("اهتزاز") || lower.contains("عادي") || lower.contains("رنين")) return IntentType.SET_RINGER

        return IntentType.UNKNOWN
    }

    private fun isFindPhoneQuery(lower: String): Boolean {
        val findWords = listOf("وينك", "وين تليفوني", "وين هاتفي", "أين هاتفي", "اين هاتفي", "أين أنت", "اين انت", "find my phone")
        return findWords.any { lower.contains(it) }
    }

    private fun isCorrection(text: String): Boolean {
        val lower = text.lowercase().trim()
        val corrections = listOf("لكن", "لا،", "في الواقع", "غلط", "خطأ", "مش صحيح")
        return corrections.any { lower.contains(it) } && text.length > 8
    }

    private fun isSetAlarm(text: String): Boolean {
        val lower = text.lowercase()
        return (lower.contains("منبه") || lower.contains("نبهني") || lower.contains("اضبط") || lower.contains("alarm")) &&
               (lower.contains("ساعة") || lower.contains("صباح") || lower.contains("مساء") || lower.contains("ظهر") || lower.contains("فجر"))
    }

    private fun isTimeQuery(lower: String): Boolean {
        val timeWords = listOf("كم الساعة", "ماهي الساعة", "ما هي الساعة", "الساعة كام", "قداش الوقت", "الوقت الان", "time")
        return timeWords.any { lower.contains(it) } || lower == "الساعة" || lower == "الوقت"
    }

    private fun isDateQuery(lower: String): Boolean {
        val dateWords = listOf("تاريخ اليوم", "ماهو التاريخ", "اليوم كام", "ما هو اليوم", "date")
        return dateWords.any { lower.contains(it) }
    }

    private fun isBatteryQuery(lower: String): Boolean {
        val batteryWords = listOf("بطارية", "بطاريه", "الشحن", "نسبة الشحن", "battery")
        return batteryWords.any { lower.contains(it) }
    }

    private fun isFlashlightQuery(lower: String): Boolean {
        return listOf("فلاش", "كشاف", "مصباح", "ضوء", "torch").any { lower.contains(it) }
    }

    private fun isBluetoothQuery(lower: String): Boolean = lower.contains("بلوتوث") || lower.contains("bluetooth")

    private fun isGreeting(lower: String): Boolean {
        val greetings = listOf("hi", "hello", "hey", "اهلا", "أهلا", "مرحبا", "سلام", "السلام عليكم", "صباح الخير", "مساء الخير", "عسلامة", "bonjour")
        return greetings.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }
    }

    private fun isSmallTalk(lower: String): Boolean {
        val smallTalk = listOf("كيفك", "شلونك", "كيف حالك", "شنحوالك", "how are you", "من انت", "من أنت", "عرفني بنفسك", "شكرا", "شكراً", "يعطيك العافية", "مين انت", "هل انتي", "هل انت")
        return smallTalk.any { lower.contains(it) }
    }

    private fun isPrayerQuery(lower: String): Boolean {
        return listOf("مواقيت الصلاة", "اوقات الصلاة", "وقت الصلاة", "الاذان", "الأذان").any { lower.contains(it) }
    }
}
