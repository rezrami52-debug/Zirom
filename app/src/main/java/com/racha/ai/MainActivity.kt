package com.racha.ai

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.racha.ai.core.locale.LocaleManager
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.services.tts.TextToSpeechHelper
import com.racha.ai.services.weather.WeatherScheduler
import com.racha.ai.ui.navigation.RachaApp
import com.racha.ai.ui.theme.RachaTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val appPrefs = AppPreferences(newBase)
        val lang = appPrefs.getLanguage()
        super.attachBaseContext(LocaleManager.applyLocale(newBase, lang))
    }

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
