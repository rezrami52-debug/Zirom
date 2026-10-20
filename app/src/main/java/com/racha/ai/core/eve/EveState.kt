package com.racha.ai.core.eve

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EveStatus {
    IDLE, NOTIFYING, AWAKE, LISTENING, THINKING, SPEAKING, PROJECTING
}

object EveState {
    private val _status = MutableStateFlow(EveStatus.IDLE)
    val status: StateFlow<EveStatus> = _status.asStateFlow()

    private val _pendingNotifications = MutableStateFlow(0)
    val pendingNotifications: StateFlow<Int> = _pendingNotifications.asStateFlow()

    fun setStatus(newStatus: EveStatus) {
        _status.value = newStatus
    }

    fun addNotification() {
        _pendingNotifications.value += 1
        if (_status.value == EveStatus.IDLE) {
            _status.value = EveStatus.NOTIFYING
        }
    }

    fun clearNotifications() {
        _pendingNotifications.value = 0
        if (_status.value == EveStatus.NOTIFYING) {
            _status.value = EveStatus.IDLE
        }
    }
}
