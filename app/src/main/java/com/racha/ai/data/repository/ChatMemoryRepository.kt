package com.racha.ai.data.repository

import android.content.Context
import com.racha.ai.data.local.ChatMessageEntity
import com.racha.ai.data.local.RachaDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Repository لحفظ محادثات Racha.
 * يتيح "الذاكرة" — Racha يتذكر ما قلته سابقاً.
 */
class ChatMemoryRepository(context: Context) {

    private val dao = RachaDatabase.getDatabase(context).chatMessageDao()

    companion object {
        const val CONV_ID = "default"
        private const val MAX_MESSAGES = 500
        private const val CONTEXT_MESSAGES = 20  // عدد الرسائل المُرسَلة لـ Gemini كسياق
    }

    fun getConversation(): Flow<List<ChatMessageEntity>> = dao.getByConversation(CONV_ID)

    suspend fun getRecentForContext(): List<ChatMessageEntity> {
        return dao.getRecentMessages(CONV_ID, CONTEXT_MESSAGES).reversed()
    }

    suspend fun saveMessage(message: ChatMessageEntity) {
        dao.insert(message)
        // تنظيف: احتفظ بآخر 500 رسالة فقط
        val count = dao.count()
        if (count > MAX_MESSAGES) {
            dao.deleteOldest(CONV_ID, count - MAX_MESSAGES)
        }
    }

    suspend fun clearAll() {
        dao.clearConversation(CONV_ID)
    }

    fun contextSize(): Int = CONTEXT_MESSAGES
}
