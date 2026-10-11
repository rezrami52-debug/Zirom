package com.racha.ai.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racha.ai.domain.model.WeatherInfo

@Composable
fun WeatherBanner(weather: WeatherInfo?) {
    if (weather == null) return

    val emoji = weatherEmoji(weather.weatherCode)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // الجهة اليسرى: الإيموجي + المدينة + الوصف
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = emoji, fontSize = 28.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                if (weather.cityName.isNotBlank()) {
                    Text(
                        text = weather.cityName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = weather.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // الجهة اليمنى: حرارة + رطوبة + رياح
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${weather.temperature.toInt()}°",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "💧 ${weather.humidity}% • 💨 ${weather.windSpeed.toInt()} كم/س",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun weatherEmoji(code: Int): String = when (code) {
    0, 1 -> "☀️"
    2 -> "⛅"
    3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
    71, 73, 75, 77, 85, 86 -> "❄️"
    95, 96, 99 -> "⛈️"
    else -> "🌡️"
}
