package com.racha.ai.core.apps

object IntentClassifier {

    enum class IntentType {
        SHOW_LOCATION, OPEN_MAP, SEND_MESSAGE, MAKE_CALL,
        SEND_EMAIL, OPEN_BROWSER, OPEN_CAMERA, PLAY_MUSIC,
        OPEN_APP, LIST_APPS, SET_ALARM, TOGGLE_WIFI, SET_RINGER,
        GET_TIME, GET_DATE, GET_BATTERY, TOGGLE_FLASHLIGHT,
        TOGGLE_BLUETOOTH, GREETING, SMALL_TALK, PRAYER_TIMES,
        CORRECTION, UNKNOWN
    }

    fun classify(text: String): IntentType {
        val lower = text.lowercase().trim()

        if (isCorrection(text)) return IntentType.CORRECTION
        if (isSetAlarm(text)) return IntentType.SET_ALARM

        // 1. الوقت والساعة
        if (isTimeQuery(lower)) return IntentType.GET_TIME

        // 2. التاريخ واليوم
        if (isDateQuery(lower)) return IntentType.GET_DATE

        // 3. البطارية والشحن
        if (isBatteryQuery(lower)) return IntentType.GET_BATTERY

        // 4. الكشاف / الفلاش
        if (isFlashlightQuery(lower)) return IntentType.TOGGLE_FLASHLIGHT

        // 5. البلوتوث
        if (isBluetoothQuery(lower)) return IntentType.TOGGLE_BLUETOOTH

        // 6. التحيات والترحيب الفوري
        if (isGreeting(lower)) return IntentType.GREETING

        // 7. مواقيت الصلاة
        if (isPrayerQuery(lower)) return IntentType.PRAYER_TIMES

        // 8. الكلام العفوي والشكر وحالة المساعد
        if (isSmallTalk(lower)) return IntentType.SMALL_TALK

        // 9. الأوامر العادية
        if (lower.contains("تطبيق") && (lower.contains("تفقد") || lower.contains("اعرض") || lower.contains("قائمة") || lower.contains("كل"))) return IntentType.LIST_APPS
        if (lower.contains("اين") && lower.contains("انا")) return IntentType.SHOW_LOCATION
        if ((lower.contains("خريطة") || lower.contains("موقعي") || lower.contains("earth") || lower.contains("maps")) && (lower.contains("اعرض") || lower.contains("افتح") || lower.contains("ارني") || lower.contains("موقعي"))) return IntentType.OPEN_MAP
        if ((lower.contains("رسالة") || lower.contains("رساله")) && (lower.contains("ارسل") || lower.contains("أرسل") || lower.contains("ابعث") || lower.contains("بعث"))) return IntentType.SEND_MESSAGE
        if (lower.contains("اتصل") || lower.contains("اتصال") || lower.contains("call") || lower.contains("appel")) return IntentType.MAKE_CALL
        if ((lower.contains("ايميل") || lower.contains("بريد") || lower.contains("email")) && (lower.contains("ارسل") || lower.contains("أرسل"))) return IntentType.SEND_EMAIL
        if (lower.contains("متصفح") || lower.contains("chrome") || lower.contains("firefox")) return IntentType.OPEN_BROWSER
        if (lower.contains("كاميرا") && (lower.contains("افتح") || lower.contains("شغل"))) return IntentType.OPEN_CAMERA
        if (lower.contains("موسيقى") || lower.contains("اغنية") || lower.contains("spotify")) return IntentType.PLAY_MUSIC
        if (lower.contains("افتح") && (lower.contains("تطبيق") || lower.length > 5)) return IntentType.OPEN_APP

        if (lower.contains("واي فاي") || lower.contains("wifi") || lower.contains("wi-fi")) return IntentType.TOGGLE_WIFI
        if ((lower.contains("صامت") || lower.contains("اهتزاز") || lower.contains("عادي") || lower.contains("رنين")) && (lower.contains("وضع") || lower.contains("حول") || lower.contains("غير"))) return IntentType.SET_RINGER

        return IntentType.UNKNOWN
    }

    private fun isCorrection(text: String): Boolean {
        val lower = text.lowercase().trim()
        val corrections = listOf("لكن", "لكني", "لكنه", "لكنها", "لا،", "لا ", "في الحقيقة", "في الواقع", "بالعكس", "غلط", "خطأ", "مش صحيح")
        return corrections.any { lower.contains(it) } && text.length > 8
    }

    private fun isSetAlarm(text: String): Boolean {
        val lower = text.lowercase()
        return (lower.contains("منبه") || lower.contains("نبهني") || lower.contains("اضبط") || lower.contains("اضبطي") || lower.contains("alarm")) &&
               (lower.contains("ساعة") || lower.contains("الساعة") || lower.contains("صباح") || lower.contains("مساء") || lower.contains("ظهر") || lower.contains("فجر"))
    }

    private fun isTimeQuery(lower: String): Boolean {
        val timeWords = listOf("كم الساعة", "ماهي الساعة", "ما هي الساعة", "الساعة كام", "قداش الوقت", "الوقت الان", "الوقت الآن", "الساعة الآن", "الساعة الان", "time")
        return timeWords.any { lower.contains(it) } || lower == "الساعة" || lower == "الوقت"
    }

    private fun isDateQuery(lower: String): Boolean {
        val dateWords = listOf("تاريخ اليوم", "ماهو التاريخ", "ما هو التاريخ", "تاريخ كام", "اليوم كام", "ما هو اليوم", "اي يوم", "تاريخ", "date")
        return dateWords.any { lower.contains(it) }
    }

    private fun isBatteryQuery(lower: String): Boolean {
        val batteryWords = listOf("بطارية", "بطاريه", "الشحن", "نسبة الشحن", "نسبه الشحن", "شحن الهاتف", "battery")
        return batteryWords.any { lower.contains(it) }
    }

    private fun isFlashlightQuery(lower: String): Boolean {
        val flashWords = listOf("فلاش", "كشاف", "مصباح", "ضوء", "torch", "flashlight")
        return flashWords.any { lower.contains(it) }
    }

    private fun isBluetoothQuery(lower: String): Boolean {
        return lower.contains("بلوتوث") || lower.contains("bluetooth")
    }

    private fun isGreeting(lower: String): Boolean {
        val greetings = listOf("hi", "hello", "hey", "اهلا", "أهلا", "مرحبا", "مرحباً", "سلام", "السلام عليكم", "صباح الخير", "مساء الخير", "يا هلا", "عسلامة", "عسلامه", "salut", "bonjour")
        return greetings.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }
    }

    private fun isSmallTalk(lower: String): Boolean {
        val smallTalk = listOf("كيفك", "شلونك", "كيف حالك", "شحالك", "شنحوالك", "من انت", "من أنت", "عرفني بنفسك", "شكرا", "شكراً", "يعطيك العافية", "مين انت", "بخير")
        return smallTalk.any { lower.contains(it) }
    }

    private fun isPrayerQuery(lower: String): Boolean {
        val prayerWords = listOf("مواقيت الصلاة", "اوقات الصلاة", "وقت الصلاة", "صلاة الفجر", "صلاة الظهر", "صلاة العصر", "صلاة المغرب", "صلاة العشاء", "موعد الصلاة", "متى الاذان", "متى الأذان")
        return prayerWords.any { lower.contains(it) }
    }
}
