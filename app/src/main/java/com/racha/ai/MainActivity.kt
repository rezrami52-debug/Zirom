package com.racha.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.racha.ai.services.tts.TextToSpeechHelper
import com.racha.ai.services.weather.WeatherScheduler
import com.racha.ai.ui.navigation.RachaApp
import com.racha.ai.ui.theme.RachaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TextToSpeechHelper.initialize(applicationContext)
        WeatherScheduler.schedule(applicationContext)

        setContent {
            RachaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RachaApp()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            TextToSpeechHelper.shutdown()
        }
    }
}
