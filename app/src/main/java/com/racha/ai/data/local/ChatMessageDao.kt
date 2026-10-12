package com.racha.ai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE conversationId = :convId ORDER BY timestamp ASC")
    fun getByConversation(convId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :convId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(convId: String, limit: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE conversationId = :convId")
    suspend fun clearConversation(convId: String)

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun count(): Int

    @Query("DELETE FROM chat_messages WHERE id IN (SELECT id FROM chat_messages WHERE conversationId = :convId ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(convId: String, count: Int)
}
