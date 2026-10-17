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

    suspend fun createNewConversation(title: String = "محادثة جديدة"): String {
        val newId = UUID.randomUUID().toString()
        val conv = ConversationEntity(id = newId, title = title, timestamp = System.currentTimeMillis())
        conversationDao.insertConversation(conv)
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
        
        // ✅ التحسين الذكي: تحديث عنوان المحادثة بناءً على أول رسالة من المستخدم
        if (message.isFromUser) {
            val firstWords = message.text.take(30) + (if (message.text.length > 30) "..." else "")
            conversationDao.updateConversationTitle(conversationId, firstWords)
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        conversationDao.deleteConversation(conversationId)
        messageDao.deleteMessagesByConversation(conversationId)
    }
    
    suspend fun clearAll() {
        messageDao.deleteAllMessages()
    }

    suspend fun getRecentForContext(conversationId: String): List<ChatMessageEntity> {
        return try {
            messageDao.getRecentMessages(conversationId, 10)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
