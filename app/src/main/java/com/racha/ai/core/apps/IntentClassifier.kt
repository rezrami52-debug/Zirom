package com.racha.ai.core.apps

object IntentClassifier {

    enum class IntentType {
        SHOW_LOCATION, OPEN_MAP, SEND_MESSAGE, MAKE_CALL, SEND_EMAIL,
        OPEN_BROWSER, OPEN_CAMERA, PLAY_MUSIC, OPEN_APP, LIST_APPS,
        SET_ALARM, CORRECTION,
        // أوامر التحكم الجديدة
        TOGGLE_INTERNET, CLOSE_APP, LOCK_SCREEN, UNKNOWN
    }

    fun classify(text: String): IntentType {
        val lower = text.lowercase().trim()

        if (isCorrection(text)) return IntentType.CORRECTION
        if (isSetAlarm(text)) return IntentType.SET_ALARM

        // ===== أوامر التحكم الجديدة =====
        if (lower.contains("واي فاي") || lower.contains("انترنت") || 
            lower.contains("wifi") || lower.contains("internet") ||
            lower.contains("شبكة")) {
            return IntentType.TOGGLE_INTERNET
        }

        if ((lower.contains("اغلاق") || lower.contains("اقفل") || lower.contains("اغلق")) && 
            lower.contains("تطبيق")) {
            return IntentType.CLOSE_APP
        }

        if (lower.contains("اقفل الشاشة") || lower.contains("اطفئ الشاشة") || 
            lower.contains("lock") || lower.contains("شاشة")) {
            return IntentType.LOCK_SCREEN
        }

        // ===== الأوامر السابقة =====
        if (lower.contains("تطبيق") && (lower.contains("تفقد") || lower.contains("اعرض") || lower.contains("قائمة") || lower.contains("كل"))) {
            return IntentType.LIST_APPS
        }
        if (lower.contains("اين") && lower.contains("انا")) return IntentType.SHOW_LOCATION
        if ((lower.contains("خريطة") || lower.contains("موقعي") || lower.contains("earth") || lower.contains("maps")) && (lower.contains("اعرض") || lower.contains("افتح") || lower.contains("ارني"))) {
            return IntentType.OPEN_MAP
        }
        if ((lower.contains("رسالة") || lower.contains("رساله")) && (lower.contains("ارسل") || lower.contains("أرسل") || lower.contains("ابعث"))) {
            return IntentType.SEND_MESSAGE
        }
        if (lower.contains("اتصل") || lower.contains("اتصال") || lower.contains("call") || lower.contains("appel")) {
            return IntentType.MAKE_CALL
        }
        if ((lower.contains("ايميل") || lower.contains("بريد") || lower.contains("email")) && (lower.contains("ارسل") || lower.contains("أرسل"))) {
            return IntentType.SEND_EMAIL
        }
        if (lower.contains("متصفح") || lower.contains("chrome") || lower.contains("firefox")) {
            return IntentType.OPEN_BROWSER
        }
        if (lower.contains("كاميرا") && (lower.contains("افتح") || lower.contains("شغل"))) {
            return IntentType.OPEN_CAMERA
        }
        if (lower.contains("موسيقى") || lower.contains("اغنية") || lower.contains("spotify")) {
            return IntentType.PLAY_MUSIC
        }
        if (lower.contains("افتح") && lower.contains("تطبيق")) {
            return IntentType.OPEN_APP
        }

        return IntentType.UNKNOWN
    }

    private fun isCorrection(text: String): Boolean {
        val lower = text.lowercase().trim()
        val corrections = listOf("لكن", "لكني", "لكنه", "لكنها", "لا،", "لا ", "في الحقيقة", "في الواقع", "بالعكس", "غلط", "خطأ", "مش صحيح")
        return corrections.any { lower.contains(it) } && text.length > 8
    }

    private fun isSetAlarm(text: String): Boolean {
        val lower = text.lowercase()
        return (lower.contains("منبه") || lower.contains("نبهني") || lower.contains("اضبط") || lower.contains("alarm")) &&
               (lower.contains("ساعة") || lower.contains("الساعة") || lower.contains("صباح") || lower.contains("مساء") || lower.contains("زوال") || lower.contains("ظهر") || lower.contains("فجر"))
    }
}
