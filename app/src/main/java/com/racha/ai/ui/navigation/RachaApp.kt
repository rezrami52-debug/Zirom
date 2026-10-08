package com.racha.ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.racha.ai.ui.screens.SettingsScreen
import com.racha.ai.ui.screens.WelcomeScreen
import com.racha.ai.ui.screens.chat.ChatScreen
import com.racha.ai.ui.screens.notifications.NotificationsScreen
import com.racha.ai.ui.screens.prayer.PrayerScreen

sealed class Screen {
    object Welcome : Screen()
    object Settings : Screen()
    object Chat : Screen()
    object Notifications : Screen()
    object Prayer : Screen()
}

@Composable
fun RachaApp() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Welcome) }

    when (currentScreen) {
        is Screen.Welcome -> WelcomeScreen(
            onOpenSettings = { currentScreen = Screen.Settings },
            onOpenChat = { currentScreen = Screen.Chat },
            onOpenNotifications = { currentScreen = Screen.Notifications },
            onOpenPrayer = { currentScreen = Screen.Prayer }
        )
        is Screen.Settings -> SettingsScreen(
            onBack = { currentScreen = Screen.Welcome }
        )
        is Screen.Chat -> ChatScreen(
            onOpenSettings = { currentScreen = Screen.Settings }
        )
        is Screen.Notifications -> NotificationsScreen(
            onBack = { currentScreen = Screen.Welcome }
        )
        is Screen.Prayer -> PrayerScreen(
            onBack = { currentScreen = Screen.Welcome }
        )
    }
}
