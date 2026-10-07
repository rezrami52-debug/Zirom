package com.racha.ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.racha.ai.ui.screens.SettingsScreen
import com.racha.ai.ui.screens.WelcomeScreen

sealed class Screen {
    object Welcome : Screen()
    object Settings : Screen()
}

@Composable
fun RachaApp() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Welcome) }

    when (currentScreen) {
        is Screen.Welcome -> WelcomeScreen(
            onOpenSettings = { currentScreen = Screen.Settings }
        )
        is Screen.Settings -> SettingsScreen(
            onBack = { currentScreen = Screen.Welcome }
        )
    }
}
