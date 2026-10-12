package com.racha.ai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * سجل محاولة فتح Racha.
 */
@Entity(tableName = "lock_attempts")
data class LockAttemptEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean,
    val photoPath: String? = null   // مسار الصورة إن وُجدت
)
