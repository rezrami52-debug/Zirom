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
                            // ربط زر الإعدادات
                        },
                        onOpenNotifications = {
                            // ربط زر الإشعارات
                        },
                        onOpenPrayer = {
                            // ربط زر أوقات الصلاة
                        },
                        onOpenRoutine = {
                            // ربط زر الروتين
                        }
                    )
                }
            }
        }
    }
}
