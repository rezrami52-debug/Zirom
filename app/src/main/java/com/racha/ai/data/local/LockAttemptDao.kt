package com.racha.ai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LockAttemptDao {

    @Query("SELECT * FROM lock_attempts ORDER BY timestamp DESC")
    fun getAll(): Flow<List<LockAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attempt: LockAttemptEntity)

    @Query("SELECT COUNT(*) FROM lock_attempts WHERE success = 0 AND timestamp > :since")
    suspend fun countFailuresSince(since: Long): Int

    @Query("SELECT * FROM lock_attempts WHERE success = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentFailures(limit: Int): List<LockAttemptEntity>

    @Query("DELETE FROM lock_attempts WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM lock_attempts")
    suspend fun deleteAll()

    @Query("SELECT * FROM lock_attempts ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): LockAttemptEntity?
}
