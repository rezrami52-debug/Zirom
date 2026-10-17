package com.racha.ai.data.repository

import android.content.Context
import com.racha.ai.data.local.ChatMessageEntity
import com.racha.ai.data.local.ConversationEntity
import com.racha.ai.data.local.RachaDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatMemoryRepository(context: Context) {
    private val db = RachaDatabase.getDatabase(context)
    private val conversationDao = db.conversationDao()
    private val messageDao = db.chatMessageDao()

    suspend fun createNewConversation(): String {
        val newId = UUID.randomUUID().toString()
        conversationDao.insertConversation(ConversationEntity(id = newId))
        return newId
    }

    fun getAllConversations(): Flow<List<ConversationEntity>> {
        return conversationDao.getAllConversations()
    }

    fun getMessagesByConversation(conversationId: String): Flow<List<ChatMessageEntity>> {
        return messageDao.getMessagesByConversation(conversationId)
    }

    suspend fun saveMessage(conversationId: String, message: ChatMessageEntity) {
        messageDao.insertMessage(message.copy(conversationId = conversationId))
    }

    suspend fun deleteConversation(conversationId: String) {
        conversationDao.deleteConversation(conversationId)
        messageDao.deleteMessagesByConversation(conversationId)
    }
    
    suspend fun clearAll() {
        messageDao.deleteAllMessages()
    }
}
