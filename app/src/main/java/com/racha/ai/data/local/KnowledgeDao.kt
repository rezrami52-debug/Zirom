package com.racha.ai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDao {

    @Query("SELECT * FROM knowledge_entries ORDER BY lastUsed DESC")
    fun getAll(): Flow<List<KnowledgeEntity>>

    /**
     * بحث نصي بسيط في السؤال + الإجابة + الكلمات المفتاحية.
     * (سيتم تحسينه لاحقاً بـ FTS إذا احتجنا).
     */
    @Query("""
        SELECT * FROM knowledge_entries
        WHERE question LIKE '%' || :query || '%'
           OR answer LIKE '%' || :query || '%'
           OR keywords LIKE '%' || :query || '%'
        ORDER BY hitCount DESC, lastUsed DESC
        LIMIT :limit
    """)
    suspend fun search(query: String, limit: Int = 5): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KnowledgeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: KnowledgeEntity)

    @Update
    suspend fun update(entry: KnowledgeEntity)

    @Query("DELETE FROM knowledge_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM knowledge_entries")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM knowledge_entries")
    suspend fun count(): Int

    /**
     * زيادة hitCount عند الاستخدام.
     */
    @Query("UPDATE knowledge_entries SET hitCount = hitCount + 1, lastUsed = :now WHERE id = :id")
    suspend fun incrementHit(id: String, now: Long = System.currentTimeMillis())
}
