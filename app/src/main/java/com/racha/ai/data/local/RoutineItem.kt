package com.racha.ai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * نوع التكرار.
 */
enum class RepeatType {
    DAILY,       // كل يوم
    WEEKDAYS,    // أيام العمل (الإثنين-الجمعة)
    WEEKEND,     // نهاية الأسبوع
    ONCE         // مرة واحدة
}

/**
 * عنصر في الروتين اليومي.
 */
@Entity(tableName = "routine_items")
data class RoutineItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,                // "شرب الماء"
    val description: String = "",     // "اشرب كوباً كاملاً"
    val emoji: String = "⏰",         // "💧"
    val hour: Int,                    // 10
    val minute: Int,                  // 0
    val isEnabled: Boolean = true,
    val repeat: String = RepeatType.DAILY.name,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * الوصف الكامل للعرض.
     */
    fun timeString(): String {
        val h = hour.toString().padStart(2, '0')
        val m = minute.toString().padStart(2, '0')
        return "$h:$m"
    }

    fun repeatType(): RepeatType {
        return try {
            RepeatType.valueOf(repeat)
        } catch (e: Exception) {
            RepeatType.DAILY
        }
    }
}
