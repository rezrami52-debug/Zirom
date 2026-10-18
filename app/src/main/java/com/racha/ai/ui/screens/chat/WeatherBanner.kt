package com.racha.ai.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racha.ai.domain.model.WeatherInfo

@Composable
fun WeatherBanner(weather: WeatherInfo?, modifier: Modifier = Modifier) {
    if (weather == null) return

    val conditionEmoji = when {
        weather.temperature > 30 -> "☀️ مشمس وحار"
        weather.temperature > 20 -> "🌤️ طقس لطيف"
        weather.temperature > 10 -> "⛅ غائم جزئياً"
        else -> "🌧️ ممطر بارد"
    }

    val glassBg = listOf(Color(0x35FFFFFF), Color(0x1560A5FA))
    val glassBorder = Color(0x55FFFFFF)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(glassBg))
                .border(1.dp, glassBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${weather.temperature}°C  |  $conditionEmoji",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "📍 ${weather.cityName ?: "سوسة"}  •  💧 الرطوبة: 86%",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "💨 رياح نشطة",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "5.0 كم/س",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}
