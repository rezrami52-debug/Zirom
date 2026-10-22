package com.racha.ai.core.eve

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EveStatus {
    IDLE, NOTIFYING, AWAKE, LISTENING, THINKING, SPEAKING, PROJECTING, MEDIA_PLAYING
}

object EveState {
    private val _status = MutableStateFlow(EveStatus.IDLE)
    val status: StateFlow<EveStatus> = _status.asStateFlow()

    private val _pendingNotifications = MutableStateFlow(0)
    val pendingNotifications: StateFlow<Int> = _pendingNotifications.asStateFlow()

    // حالة الوسائط
    private val _mediaUrl = MutableStateFlow<String?>(null)
    val mediaUrl: StateFlow<String?> = _mediaUrl.asStateFlow()

    private val _isVideo = MutableStateFlow(false)
    val isVideo: StateFlow<Boolean> = _isVideo.asStateFlow()

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

    fun startMedia(url: String, video: Boolean) {
        _mediaUrl.value = url
        _isVideo.value = video
        _status.value = EveStatus.MEDIA_PLAYING
    }

    fun stopMedia() {
        _mediaUrl.value = null
        _isVideo.value = false
        _status.value = EveStatus.IDLE
    }
}
