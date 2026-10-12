package com.racha.ai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * الملف الشخصي للمستخدم — 15 حقل.
 * يُخزَّن صف واحد فقط (id = 1 دائماً).
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey
    val id: Int = 1,

    // معلومات شخصية
    val name: String = "",
    val city: String = "",

    // العمل
    val occupation: String = "",
    val workDays: String = "",        // "الإثنين-الجمعة"
    val workStartHour: Int = 8,
    val workStartMinute: Int = 0,
    val workEndHour: Int = 17,
    val workEndMinute: Int = 0,

    // النوم
    val wakeUpHour: Int = 7,
    val wakeUpMinute: Int = 0,
    val sleepHour: Int = 22,
    val sleepMinute: Int = 30,

    // الوجبات
    val breakfastHour: Int = 8,
    val breakfastMinute: Int = 0,
    val lunchHour: Int = 13,
    val lunchMinute: Int = 0,
    val dinnerHour: Int = 20,
    val dinnerMinute: Int = 0,

    // الرياضة
    val sportType: String = "",       // "جري، كرة قدم"
    val sportDays: String = "",       // "الثلاثاء، الخميس"

    // الاهتمامات
    val hobbies: String = "",         // "قراءة، طبخ"
    val notes: String = "",           // ملاحظات حرة

    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * هل الملف مُعبَّأ (غير فارغ)؟
     */
    fun isFilled(): Boolean {
        return name.isNotBlank() || occupation.isNotBlank() ||
               hobbies.isNotBlank() || notes.isNotBlank()
    }

    /**
     * ملخص نصي للحقن في System Prompt.
     */
    fun toPromptContext(): String {
        val lines = mutableListOf<String>()
        if (name.isNotBlank()) lines.add("- الاسم: $name")
        if (city.isNotBlank()) lines.add("- المدينة: $city")
        if (occupation.isNotBlank()) lines.add("- المهنة: $occupation")
        if (workDays.isNotBlank()) {
            lines.add("- أيام العمل: $workDays")
            lines.add("- ساعات العمل: ${pad(workStartHour)}:${pad(workStartMinute)} - ${pad(workEndHour)}:${pad(workEndMinute)}")
        }
        lines.add("- الاستيقاظ: ${pad(wakeUpHour)}:${pad(wakeUpMinute)}")
        lines.add("- النوم: ${pad(sleepHour)}:${pad(sleepMinute)}")
        lines.add("- الفطور: ${pad(breakfastHour)}:${pad(breakfastMinute)}")
        lines.add("- الغداء: ${pad(lunchHour)}:${pad(lunchMinute)}")
        lines.add("- العشاء: ${pad(dinnerHour)}:${pad(dinnerMinute)}")
        if (sportType.isNotBlank()) {
            lines.add("- الرياضة: $sportType")
            if (sportDays.isNotBlank()) lines.add("- أيام الرياضة: $sportDays")
        }
        if (hobbies.isNotBlank()) lines.add("- الهوايات: $hobbies")
        if (notes.isNotBlank()) lines.add("- ملاحظات: $notes")
        return lines.joinToString("\n")
    }

    private fun pad(value: Int): String = value.toString().padStart(2, '0')
}
