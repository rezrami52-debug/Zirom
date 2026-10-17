package com.racha.ai.core.apps

/**
 * قاموس لربط الأسماء العربية الشائعة بالتطبيقات
 */
object ArabicAppNames {
    
    private val arabicToEnglish = mapOf(
        "فيسبوك" to "Facebook",
        "فيس بوك" to "Facebook",
        "فيس" to "Facebook",
        "انستغرام" to "Instagram",
        "انستا" to "Instagram",
        "واتساب" to "WhatsApp",
        "واتس اب" to "WhatsApp",
        "واتس" to "WhatsApp",
        "تيليجرام" to "Telegram",
        "تليجرام" to "Telegram",
        "سناب شات" to "Snapchat",
        "سناب" to "Snapchat",
        "تيك توك" to "TikTok",
        "تيكتوك" to "TikTok",
        "يوتيوب" to "YouTube",
        "يوتوب" to "YouTube",
        "تويتر" to "Twitter",
        "اكس" to "X",
        "إكس" to "X",
        "سناب شات" to "Snapchat",
        "ماسنجر" to "Messenger",
        "مسنجر" to "Messenger",
        "كروم" to "Chrome",
        "جوجل كروم" to "Chrome",
        "سفاري" to "Safari",
        "خرائط" to "Maps",
        "جوجل ماب" to "Maps",
        "جوجل ايرث" to "Google Earth",
        "ارث" to "Earth",
        "كاميرا" to "Camera",
        "معرض" to "Gallery",
        "صور" to "Photos",
        "موسيقى" to "Music",
        "سبوتيفاي" to "Spotify",
        "ساوند كلاود" to "SoundCloud",
        "نيتفليكس" to "Netflix",
        "نتفليكس" to "Netflix",
        "ديسكورد" to "Discord",
        "زوم" to "Zoom",
        "سكايب" to "Skype",
        "ملفات" to "Files",
        "تطبيقات" to "Apps",
        "إعدادات" to "Settings",
        "ضبط" to "Settings",
        "حاسبة" to "Calculator",
        "آلة حاسبة" to "Calculator",
        "تقويم" to "Calendar",
        "كalendar" to "Calendar",
        "ساعة" to "Clock",
        "منبه" to "Clock",
        "طقس" to "Weather",
        "ملاحظات" to "Notes",
        "مذكرات" to "Notes",
        "بريد" to "Gmail",
        "جيميل" to "Gmail",
        "اوت لوك" to "Outlook",
        "دوكس" to "Docs",
        "مستندات" to "Docs",
        "شيت" to "Sheets",
        "جداول" to "Sheets",
        "درايف" to "Drive",
        "قوقل درايف" to "Drive",
        "ترجمة" to "Translate",
        "مترجم" to "Translate"
    )
    
    /**
     * تحويل الاسم العربي إلى إنجليزي
     */
    fun toEnglish(arabicName: String): String {
        return arabicToEnglish[arabicName.trim()] ?: arabicName
    }
    
    /**
     * التحقق مما إذا كان النص اسماً عربياً لتطبيق
     */
    fun isArabicAppName(text: String): Boolean {
        val normalized = normalizeArabic(text.trim().lowercase())
        return arabicToEnglish.keys.any { 
            normalizeArabic(it).contains(normalized) || 
            normalized.contains(normalizeArabic(it))
        }
    }
    
    /**
     * البحث عن اسم التطبيق الإنجليزي المقابل
     */
    fun findEnglishName(arabicText: String): String? {
        val normalized = normalizeArabic(arabicText.trim().lowercase())
        
        // بحث مباشر
        arabicToEnglish.keys.forEach { arabicName ->
            if (normalizeArabic(arabicName) == normalized) {
                return arabicToEnglish[arabicName]
            }
        }
        
        // بحث جزئي
        arabicToEnglish.keys.forEach { arabicName ->
            if (normalizeArabic(arabicName).contains(normalized)) {
                return arabicToEnglish[arabicName]
            }
        }
        
        return null
    }
    
    private fun normalizeArabic(text: String): String {
        return text
            .replace("إ", "ا")
            .replace("أ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace("ؤ", "و")
            .replace("ئ", "ي")
            .replace("ء", "")
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace(" ", "")
            .lowercase()
            .trim()
    }
}
