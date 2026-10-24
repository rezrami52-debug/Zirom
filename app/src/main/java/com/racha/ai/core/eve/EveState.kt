package com.racha.ai.core.eve

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EveMood {
    SLEEPING, AWAKE_SMILING, OBSERVING_SCREEN, NOTIFICATION_ALERT, MEDIA_PLAYING
}

enum class MediaShelfType {
    NONE, PHOTOS, VIDEOS, MUSIC
}

data class MediaPlaybackState(
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = ""
)

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
    val config: EveCustomizationConfig = EveCustomizationConfig(),
    val playback: MediaPlaybackState = MediaPlaybackState(),
    val shelfType: MediaShelfType = MediaShelfType.NONE
)

object EveState {
    private val _uiState = MutableStateFlow(EveUiState())
    val uiState: StateFlow<EveUiState> = _uiState.asStateFlow()

    private val _status = MutableStateFlow(EveStatus.IDLE)
    val status: StateFlow<EveStatus> = _status.asStateFlow()

    private val _pendingNotifications = MutableStateFlow(0)
    val pendingNotifications: StateFlow<Int> = _pendingNotifications.asStateFlow()

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

    fun updatePlayback(isPlaying: Boolean, title: String, artist: String = "") {
        _uiState.value = _uiState.value.copy(
            playback = MediaPlaybackState(isPlaying = isPlaying, title = title, artist = artist)
        )
    }

    fun openShelf(type: MediaShelfType) {
        _uiState.value = _uiState.value.copy(shelfType = type)
    }

    fun closeShelf() {
        _uiState.value = _uiState.value.copy(shelfType = MediaShelfType.NONE)
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
}

enum class EveStatus {
    IDLE, NOTIFYING, AWAKE, LISTENING, THINKING, SPEAKING, PROJECTING, MEDIA_PLAYING
}
