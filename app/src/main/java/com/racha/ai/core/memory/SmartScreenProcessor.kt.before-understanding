package com.racha.ai.core.memory

object SmartScreenProcessor {

    // كلمات واجهة المستخدم التي لا تحمل قيمة تعليمية ونريد تجاهلها
    private val uiNoise = setOf(
        "رجوع", "عودة", "موافق", "إلغاء", "حفظ", "مشاركة", "إرسال",
        "back", "home", "menu", "ok", "cancel", "send", "share",
        "android.widget", "android.view"
    )

    fun cleanAndFormat(rawText: String): String {
        if (rawText.isBlank()) return ""

        return rawText
            // 1. تحويل \n\n الحرفية إلى أسطر جديدة حقيقية
            .replace("\\n\\n", "\n")
            .replace("\\n", "\n")
            // 2. تقسيم النص إلى أسطر
            .split("\n")
            // 3. تنقية كل سطر
            .map { it.trim() }
            .filter { line ->
                line.length > 3 && // تجاهل الرموز القصيرة جداً
                !uiNoise.any { noise -> line.equals(noise, ignoreCase = true) } && // تجاهل أزرار الواجهة
                !line.startsWith("android.") // تجاهل أسماء الفئات البرمجية الداخلية
            }
            // 4. إزالة التكرار المتتالي
            .distinct()
            // 5. إعادة التجميع بتنسيق نظيف
            .joinToString("\n• ") 
            .let { "• $it" } // إضافة نقطة في البداية
            .take(1500) // حد أقصى معقول لتجنب ازدحام الذاكرة
    }

    fun isLikelyCodeOrTechnical(text: String): Boolean {
        return text.contains(Regex("(?i)fun |val |var |class |import |package |\\{ |\\}"))
    }
}
