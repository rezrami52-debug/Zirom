package com.racha.ai.domain.model

import java.util.UUID

data class AppNotification(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
