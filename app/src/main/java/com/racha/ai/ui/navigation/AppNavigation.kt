package com.racha.ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.racha.ai.ui.screens.chat.ChatScreen
import com.racha.ai.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation(onToggleBubble: () -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Chat.route) {
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onToggleBubble = onToggleBubble
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onToggleBubble = onToggleBubble
            )
        }
    }
}
