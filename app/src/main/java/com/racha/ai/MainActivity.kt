package com.racha.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.racha.ai.ui.navigation.RachaApp
import com.racha.ai.ui.theme.RachaTheme
import com.racha.ai.ui.theme.isNightTime
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var isNight by remember { mutableStateOf(isNightTime()) }

            LaunchedEffect(Unit) {
                while (true) {
                    delay(60_000)
                    isNight = isNightTime()
                }
            }

            RachaTheme(darkTheme = isNight) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RachaApp(enableBiometricLock = false)
                }
            }
        }
    }
}
