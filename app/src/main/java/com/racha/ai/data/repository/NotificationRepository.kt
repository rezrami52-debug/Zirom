package com.racha.ai.data.repository

import com.racha.ai.domain.model.AppNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository بسيط لتخزين الإشعارات في الذاكرة.
 */
object NotificationRepository {

    private const val MAX_NOTIFICATIONS = 100

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    // عدد الإشعارات غير المقروءة
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    fun add(notification: AppNotification) {
        val current = _notifications.value
        val updated = (listOf(notification) + current).take(MAX_NOTIFICATIONS)
        _notifications.value = updated
        _unreadCount.value = _unreadCount.value + 1
    }

    /**
     * تحديد كل الإشعارات كمقروءة.
     */
    fun markAllRead() {
        _unreadCount.value = 0
    }

    fun clear() {
        _notifications.value = emptyList()
        _unreadCount.value = 0
    }
}
