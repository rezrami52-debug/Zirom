package com.racha.ai.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Query("SELECT * FROM routine_items ORDER BY hour ASC, minute ASC")
    fun getAll(): Flow<List<RoutineItem>>

    @Query("SELECT * FROM routine_items WHERE isEnabled = 1 ORDER BY hour ASC, minute ASC")
    fun getEnabled(): Flow<List<RoutineItem>>

    @Query("SELECT * FROM routine_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RoutineItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: RoutineItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RoutineItem>)

    @Update
    suspend fun update(item: RoutineItem)

    @Delete
    suspend fun delete(item: RoutineItem)

    @Query("DELETE FROM routine_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM routine_items")
    suspend fun count(): Int

    @Query("DELETE FROM routine_items")
    suspend fun deleteAll()
}
