package com.racha.ai.core.command

sealed interface RachaCommand {
    data class AskAI(val text: String) : RachaCommand
    data class Navigate(val destination: String) : RachaCommand
    data object FindNearestPavedRoad : RachaCommand
    data class OpenApp(val appName: String) : RachaCommand
    data object OpenMap : RachaCommand
    data class PlayMedia(val query: String) : RachaCommand
    data object ReadScreen : RachaCommand
    data object GetBattery : RachaCommand
    data object GetTime : RachaCommand
    data object GetDate : RachaCommand
    data class ToggleFlashlight(val enabled: Boolean) : RachaCommand
    data object OpenVideos : RachaCommand
    data object OpenPhotos : RachaCommand
    data class SendMessage(
        val contactName: String,
        val message: String
    ) : RachaCommand
    data object LockScreen : RachaCommand
    data object Unknown : RachaCommand
}
