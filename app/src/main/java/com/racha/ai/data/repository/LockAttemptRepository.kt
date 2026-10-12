package com.racha.ai.data.repository

import android.content.Context
import android.util.Log
import com.racha.ai.data.local.LockAttemptEntity
import com.racha.ai.data.local.RachaDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Repository لسجل محاولات فتح Racha.
 */
class LockAttemptRepository(context: Context) {

    companion object {
        private const val TAG = "RachaLockAttempts"
        private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
    }

    private val dao = RachaDatabase.getDatabase(context).lockAttemptDao()

    fun getAll(): Flow<List<LockAttemptEntity>> = dao.getAll()

    suspend fun recordAttempt(success: Boolean, photoPath: String? = null) {
        dao.insert(
            LockAttemptEntity(
                success = success,
                photoPath = photoPath
            )
        )
        Log.d(TAG, "محاولة: success=$success")
    }

    /**
     * عدد المحاولات الفاشلة في آخر 24 ساعة.
     */
    suspend fun countRecentFailures(): Int {
        val since = System.currentTimeMillis() - ONE_DAY_MS
        return dao.countFailuresSince(since)
    }

    /**
     * تنظيف الصور القديمة (أكثر من N يوم).
     */
    suspend fun cleanupOld(daysToKeep: Int) {
        val cutoff = System.currentTimeMillis() - (daysToKeep * ONE_DAY_MS)
        dao.deleteOlderThan(cutoff)
        Log.d(TAG, "تم حذف محاولات أقدم من $daysToKeep يوم")
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    suspend fun getLatest(): LockAttemptEntity? = dao.getLatest()
}
