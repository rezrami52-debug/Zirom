package com.racha.ai.core.apps

class IntentClassifier {

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
        LIST_APPS,
        UNKNOWN
    }

    companion object {
        fun classify(text: String): IntentType {
            val lower = text.lowercase()

            // قائمة التطبيقات
            if (lower.contains("تطبيق") &&
                (lower.contains("تفقد") || lower.contains("اعرض") ||
                 lower.contains("قائمة") || lower.contains("كل"))) {
                return IntentType.LIST_APPS
            }

            // الموقع
            if (lower.contains("اين") && lower.contains("انا")) {
                return IntentType.SHOW_LOCATION
            }

            // الخريطة
            if (lower.contains("خريطة") || lower.contains("موقعي") ||
                lower.contains("google earth") || lower.contains("maps")) {
                return IntentType.OPEN_MAP
            }

            // رسالة
            if (lower.contains("رسالة") || lower.contains("ارسل") ||
                lower.contains("واتساب") || lower.contains("telegram")) {
                return IntentType.SEND_MESSAGE
            }

            // اتصال
            if (lower.contains("اتصل") || lower.contains("اتصال") ||
                lower.contains("call") || lower.contains("appel")) {
                return IntentType.MAKE_CALL
            }

            // بريد
            if (lower.contains("ايميل") || lower.contains("بريد") ||
                lower.contains("email")) {
                return IntentType.SEND_EMAIL
            }

            // متصفح
            if (lower.contains("متصفح") || lower.contains("chrome") ||
                lower.contains("firefox")) {
                return IntentType.OPEN_BROWSER
            }

            // كاميرا
            if (lower.contains("كاميرا") || lower.contains("صورة") ||
                lower.contains("camera") || lower.contains("photo")) {
                return IntentType.OPEN_CAMERA
            }

            // موسيقى
            if (lower.contains("موسيقى") || lower.contains("اغنية") ||
                lower.contains("spotify") || lower.contains("music")) {
                return IntentType.PLAY_MUSIC
            }

            // فتح تطبيق محدد
            if (lower.contains("افتح") && lower.contains("تطبيق")) {
                return IntentType.OPEN_APP
            }

            return IntentType.UNKNOWN
        }
    }
}
