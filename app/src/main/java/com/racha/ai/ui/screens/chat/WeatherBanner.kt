package com.racha.ai.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
fun WeatherBanner(
    weather: WeatherInfo?,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val conditionEmoji = when {
        weather == null -> "⚠️ لا يوجد اتصال"
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
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (weather != null) "${weather.temperature}°C  |  $conditionEmoji" else "حالة الطقس غير متوفرة",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "📍 ${weather?.cityName ?: "سوسة"}  •  💨 رياح 5 كم/س",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "تحديث الطقس",
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
