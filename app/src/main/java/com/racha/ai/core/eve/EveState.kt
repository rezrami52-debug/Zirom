package com.racha.ai.core.eve

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EveMood {
    SLEEPING, AWAKE_SMILING, OBSERVING_SCREEN, NOTIFICATION_ALERT, MEDIA_PLAYING
}

data class EveCustomizationConfig(
    val bodyColor: Color = Color(0xFFF7F9FC),
    val eyeColor: Color = Color(0xFF00E5FF),
    val eyeGlowColor: Color = Color(0x6600B0FF),
    val alertRingColor: Color = Color(0xFFFF1744),
    val faceScreenColor: Color = Color(0xFF000000),
    val backgroundAlpha: Float = 0.95f
)

data class EveUiState(
    val mood: EveMood = EveMood.SLEEPING,
    val config: EveCustomizationConfig = EveCustomizationConfig()
)

object EveState {
    private val _uiState = MutableStateFlow(EveUiState())
    val uiState: StateFlow<EveUiState> = _uiState.asStateFlow()

    private val _status = MutableStateFlow(EveStatus.IDLE)
    val status: StateFlow<EveStatus> = _status.asStateFlow()

    private val _pendingNotifications = MutableStateFlow(0)
    val pendingNotifications: StateFlow<Int> = _pendingNotifications.asStateFlow()

    private val _mediaUrl = MutableStateFlow<String?>(null)
    val mediaUrl: StateFlow<String?> = _mediaUrl.asStateFlow()

    private val _isVideo = MutableStateFlow(false)
    val isVideo: StateFlow<Boolean> = _isVideo.asStateFlow()

    /**
     * تبديل حالة EVE بين السبات (العيون مغلقة) والاستيقاظ (العيون مفتوحة)
     * @return true إذا أصبحت مستيقظة، false إذا دخلت السبات
     */
    fun toggleSleepWake(): Boolean {
        val willWakeUp = _uiState.value.mood == EveMood.SLEEPING
        if (willWakeUp) {
            setMood(EveMood.AWAKE_SMILING)
        } else {
            setMood(EveMood.SLEEPING)
        }
        return willWakeUp
    }

    fun setMood(mood: EveMood) {
        _uiState.value = _uiState.value.copy(mood = mood)
        _status.value = when (mood) {
            EveMood.SLEEPING -> EveStatus.IDLE
            EveMood.AWAKE_SMILING -> EveStatus.AWAKE
            EveMood.OBSERVING_SCREEN -> EveStatus.LISTENING
            EveMood.NOTIFICATION_ALERT -> EveStatus.NOTIFYING
            EveMood.MEDIA_PLAYING -> EveStatus.MEDIA_PLAYING
        }
    }

    fun updateConfig(config: EveCustomizationConfig) {
        _uiState.value = _uiState.value.copy(config = config)
    }

    fun setStatus(newStatus: EveStatus) {
        _status.value = newStatus
        val mood = when (newStatus) {
            EveStatus.IDLE -> EveMood.SLEEPING
            EveStatus.NOTIFYING -> EveMood.NOTIFICATION_ALERT
            EveStatus.AWAKE -> EveMood.AWAKE_SMILING
            EveStatus.LISTENING -> EveMood.OBSERVING_SCREEN
            EveStatus.THINKING -> EveMood.AWAKE_SMILING
            EveStatus.SPEAKING -> EveMood.AWAKE_SMILING
            EveStatus.PROJECTING -> EveMood.SLEEPING
            EveStatus.MEDIA_PLAYING -> EveMood.MEDIA_PLAYING
        }
        _uiState.value = _uiState.value.copy(mood = mood)
    }

    fun addNotification() {
        _pendingNotifications.value += 1
        setMood(EveMood.NOTIFICATION_ALERT)
    }

    fun clearNotifications() {
        _pendingNotifications.value = 0
        if (_status.value == EveStatus.NOTIFYING) {
            setMood(EveMood.SLEEPING)
        }
    }

    fun startMedia(url: String, video: Boolean) {
        _mediaUrl.value = url
        _isVideo.value = video
        _status.value = EveStatus.MEDIA_PLAYING
        _uiState.value = _uiState.value.copy(mood = EveMood.MEDIA_PLAYING)
    }

    fun stopMedia() {
        _mediaUrl.value = null
        _isVideo.value = false
        _status.value = EveStatus.IDLE
        _uiState.value = _uiState.value.copy(mood = EveMood.SLEEPING)
    }
}

enum class EveStatus {
    IDLE, NOTIFYING, AWAKE, LISTENING, THINKING, SPEAKING, PROJECTING, MEDIA_PLAYING
}
