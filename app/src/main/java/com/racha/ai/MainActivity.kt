package com.racha.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.racha.ai.core.util.TimeHelper
import com.racha.ai.ui.theme.RachaTheme
import com.racha.ai.ui.screens.chat.ChatScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isNight by remember { mutableStateOf(TimeHelper.isNightTime()) }

            RachaTheme(darkTheme = isNight) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen(
                        onOpenSettings = {
                            try {
                                val intent = android.content.Intent(this, com.racha.ai.ui.screens.SettingsActivity::class.java)
                                startActivity(intent)
                            } catch (e: Exception) { e.printStackTrace() }
                        },
                        onOpenNotifications = {
                            try {
                                val intent = android.content.Intent(this, com.racha.ai.ui.screens.notifications.NotificationsActivity::class.java)
                                startActivity(intent)
                            } catch (e: Exception) { e.printStackTrace() }
                        },
                        onOpenPrayer = {
                            try {
                                val intent = android.content.Intent(this, com.racha.ai.ui.screens.prayer.PrayerActivity::class.java)
                                startActivity(intent)
                            } catch (e: Exception) { e.printStackTrace() }
                        },
                        onOpenRoutine = {
                            try {
                                val intent = android.content.Intent(this, com.racha.ai.ui.screens.routine.RoutineActivity::class.java)
                                startActivity(intent)
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    )
                }
            }
        }
    }
}
