package com.racha.ai.core.apps

import com.racha.ai.core.agent.IntentMatcher

object IntentClassifier {

    enum class IntentType {
        SHOW_LOCATION,
        OPEN_MAP,
        SEND_MESSAGE,
        MAKE_CALL,
        SEND_EMAIL,
        OPEN_BROWSER,
        OPEN_CAMERA,
        PLAY_MUSIC,
        OPEN_APP,
        UNKNOWN
    }

    fun classify(input: String): IntentType {
        if (isLocationQuestion(input)) return IntentType.SHOW_LOCATION
        if (isMapRequest(input)) return IntentType.OPEN_MAP
        if (isSendMessage(input)) return IntentType.SEND_MESSAGE
        if (isMakeCall(input)) return IntentType.MAKE_CALL
        if (isSendEmail(input)) return IntentType.SEND_EMAIL
        if (isOpenBrowser(input)) return IntentType.OPEN_BROWSER
        if (isOpenCamera(input)) return IntentType.OPEN_CAMERA
        if (isPlayMusic(input)) return IntentType.PLAY_MUSIC
        if (isOpenApp(input)) return IntentType.OPEN_APP
        return IntentType.UNKNOWN
    }

    fun isLocationQuestion(input: String): Boolean {
        return IntentMatcher.containsAny(
            input,
            "اين انا", "اين انا الان", "وين انا",
            "موقعي", "مكاني", "اين اقف"
        ) && !IntentMatcher.containsAny(input, "خريطة", "خريطه", "خرائط", "maps")
    }

    fun isMapRequest(input: String): Boolean {
        val hasMapKeyword = IntentMatcher.containsAny(
            input,
            "خريطة", "خريطه", "خرائط", "خرايط",
            "earth", "ايرث", "maps", "map"
        )
        val hasActionKeyword = IntentMatcher.containsAny(
            input,
            "اعرض", "افتح", "ارني", "أرني", "ورني",
            "show", "open"
        )
        return hasMapKeyword && (hasActionKeyword ||
            IntentMatcher.containsAny(input, "موقعي", "مكاني"))
    }

    fun isSendMessage(input: String): Boolean {
        val hasVerb = IntentMatcher.containsAny(
            input,
            "ارسل", "أرسل", "بعث", "ابعت", "send"
        )
        val hasMessage = IntentMatcher.containsAny(
            input,
            "رسالة", "رساله", "message",
            "واتساب", "whatsapp", "تلغرام", "telegram"
        )
        return hasVerb && hasMessage
    }

    fun isMakeCall(input: String): Boolean {
        return IntentMatcher.containsAny(input, "اتصل", "اتصلي", "كلم", "call") &&
               !isLocationQuestion(input)
    }

    fun isSendEmail(input: String): Boolean {
        return IntentMatcher.containsAny(input, "ايميل", "إيميل", "بريد", "email", "mail") &&
               IntentMatcher.containsAny(input, "ارسل", "أرسل", "send")
    }

    fun isOpenBrowser(input: String): Boolean {
        return IntentMatcher.containsAny(input, "متصفح", "browser", "chrome", "firefox") &&
               IntentMatcher.containsAny(input, "افتح", "open")
    }

    fun isOpenCamera(input: String): Boolean {
        return IntentMatcher.containsAny(input, "كاميرا", "camera") &&
               !IntentMatcher.containsAny(input, "التقط", "صور", "photo")
    }

    fun isPlayMusic(input: String): Boolean {
        return IntentMatcher.containsAny(input, "شغل موسيقى", "شغل اغنية", "play music")
    }

    fun isOpenApp(input: String): Boolean {
        return IntentMatcher.containsAny(input, "افتح تطبيق", "open app")
    }
}
