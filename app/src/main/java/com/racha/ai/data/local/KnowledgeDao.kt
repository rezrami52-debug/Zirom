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

    @Query("""
        SELECT * FROM knowledge_entries
        WHERE question LIKE '%' || :query || '%'
           OR answer LIKE '%' || :query || '%'
           OR keywords LIKE '%' || :query || '%'
        ORDER BY hitCount DESC, lastUsed DESC
        LIMIT :limit
    """)
    suspend fun search(query: String, limit: Int = 5): List<KnowledgeEntity>

    @Query("""
        SELECT * FROM knowledge_entries
        WHERE source = 'user' AND topic = 'معجم رمزي'
        ORDER BY lastUsed DESC
    """)
    suspend fun getUserLexiconEntries(): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KnowledgeEntity?

    @Query("SELECT * FROM knowledge_entries WHERE question = :question LIMIT 1")
    suspend fun findByQuestion(question: String): KnowledgeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: KnowledgeEntity)

    @Update
    suspend fun update(entry: KnowledgeEntity)

    @Query("DELETE FROM knowledge_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM knowledge_entries")
    suspend fun deleteAll()


    @Query("DELETE FROM knowledge_entries WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("SELECT COUNT(*) FROM knowledge_entries")
    suspend fun count(): Int

    @Query("UPDATE knowledge_entries SET hitCount = hitCount + 1, lastUsed = :now WHERE id = :id")
    suspend fun incrementHit(id: String, now: Long = System.currentTimeMillis())
}
