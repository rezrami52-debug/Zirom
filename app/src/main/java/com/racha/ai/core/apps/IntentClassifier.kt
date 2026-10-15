package com.racha.ai.core.apps

import com.racha.ai.core.agent.IntentMatcher

/**
 * يصنّف نية المستخدم من النص.
 */
object IntentClassifier {

    /**
     * أنواع النوايا.
     */
    enum class IntentType {
        SHOW_LOCATION,       // "اين انا"
        OPEN_MAP,            // "اعرض موقعي على الخريطة"
        SEND_MESSAGE,        // "أرسل رسالة لأحمد"
        MAKE_CALL,           // "اتصل بأحمد"
        SEND_EMAIL,          // "أرسل إيميل"
        OPEN_BROWSER,        // "افتح المتصفح"
        OPEN_CAMERA,         // "افتح الكاميرا"
        PLAY_MUSIC,          // "شغل موسيقى"
        OPEN_APP,            // "افتح واتساب"
        UNKNOWN              // غير معروف
    }

    /**
     * كلاسيفاي النص.
     */
    fun classify(input: String): IntentType {
        // Location
        if (isLocationQuestion(input)) return IntentType.SHOW_LOCATION
        if (isMapRequest(input)) return IntentType.OPEN_MAP

        // Messaging
        if (isSendMessage(input)) return IntentType.SEND_MESSAGE
        if (isMakeCall(input)) return IntentType.MAKE_CALL
        if (isSendEmail(input)) return IntentType.SEND_EMAIL

        // App Launchers
        if (isOpenBrowser(input)) return IntentType.OPEN_BROWSER
        if (isOpenCamera(input)) return IntentType.OPEN_CAMERA
        if (isPlayMusic(input)) return IntentType.PLAY_MUSIC
        if (isOpenApp(input)) return IntentType.OPEN_APP

        return IntentType.UNKNOWN
    }

    // ===== كاشفات النية =====

    fun isLocationQuestion(input: String): Boolean {
        return IntentMatcher.containsAny(
            input,
            "اين انا", "اين انا الان", "وين انا",
            "موقعي", "مكاني", "اين اقف"
        )
    }

    fun isMapRequest(input: String): Boolean {
        return IntentMatcher.containsAny(
            input,
            "خريطة", "الخريطة", "خرائط", "الخرائط",
            "earth", "ايرث", "maps", "map"
        ) && IntentMatcher.containsAny(
            input,
            "اعرض", "افتح", "ارني", "أرني", "ورني", "show", "open"
        )
    }

    fun isSendMessage(input: String): Boolean {
        return IntentMatcher.containsAny(
            input,
            "ارسل رسالة", "أرسل رسالة", "بعث رسالة", "اكتب رسالة",
            "send message", "send whatsapp"
        )
    }

    fun isMakeCall(input: String): Boolean {
        return IntentMatcher.containsAny(
            input,
            "اتصل", "اتصلي", "كلم", "هاتف", "call"
        ) && !isLocationQuestion(input)
    }

    fun isSendEmail(input: String): Boolean {
        return IntentMatcher.containsAny(
            input,
            "ايميل", "إيميل", "بريد", "email", "mail"
        ) && IntentMatcher.containsAny(
            input,
            "ارسل", "أرسل", "اكتب", "send"
        )
    }

    fun isOpenBrowser(input: String): Boolean {
        return IntentMatcher.containsAny(
            input,
            "متصفح", "browser", "chrome", "فايرفوكس"
        ) && IntentMatcher.containsAny(
            input,
            "افتح", "open", "شغل"
        )
    }

    fun isOpenCamera(input: String): Boolean {
        return IntentMatcher.containsAny(
            input,
            "كاميرا", "الكاميرا", "camera"
        ) && !IntentMatcher.containsAny(input, "التقط", "صور", "photo")
    }

    fun isPlayMusic(input: String): Boolean {
        return IntentMatcher.containsAny(
            input,
            "شغل موسيقى", "شغل اغنية", "شغل أغنية",
            "play music", "شغل spotify"
        )
    }

    fun isOpenApp(input: String): Boolean {
        return IntentMatcher.containsAny(
            input,
            "افتح تطبيق", "شغل تطبيق", "open app"
        )
    }
}
