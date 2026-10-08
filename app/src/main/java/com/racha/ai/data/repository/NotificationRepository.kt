package com.racha.ai.data.repository

import com.racha.ai.domain.model.AppNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository بسيط لتخزين الإشعارات في الذاكرة.
 * لاحقاً سنستخدم Room.
 */
object NotificationRepository {

    private const val MAX_NOTIFICATIONS = 100

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    fun add(notification: AppNotification) {
        val current = _notifications.value
        val updated = (listOf(notification) + current).take(MAX_NOTIFICATIONS)
        _notifications.value = updated
    }

    fun clear() {
        _notifications.value = emptyList()
    }
}
