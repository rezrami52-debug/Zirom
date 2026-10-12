package com.racha.ai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val conversationId: String = "default"
)
