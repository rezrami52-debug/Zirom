package com.racha.ai.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.racha.ai.core.util.TimeHelper
import com.racha.ai.domain.model.WeatherInfo

@Composable
fun WeatherBanner(weather: WeatherInfo?, modifier: Modifier = Modifier) {
    if (weather == null) return

    val isNight = TimeHelper.isNightTime()
    
    // اختيار الأيقونة بناءً على الوقت (يمكنك استبدال الأسماء بأيقوناتك الفعلية في res/drawable)
    // مثال: R.drawable.ic_sun أو R.drawable.ic_moon
    val weatherIconText = if (isNight) "🌙" else "☀️"
    val timeText = if (isNight) "مساءً" else "صباحاً"

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isNight) MaterialTheme.colorScheme.surfaceVariant 
                             else MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${weather.temperature}°C",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = weather.cityName ?: "موقعك الحالي",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "الرطوبة: ${weather.humidity}% • الرياح: ${weather.windSpeed} كم/س",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = weatherIconText,
                    style = MaterialTheme.typography.displayLarge
                )
                Text(
                    text = weather.condition ?: (if (isNight) "صافٍ ليلاً" else "صافٍ نهاراً"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = TimeHelper.getCurrentTimeString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
