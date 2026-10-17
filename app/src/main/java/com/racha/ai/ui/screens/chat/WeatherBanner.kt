package com.racha.ai.ui.screens.chat

import androidx.compose.foundation.background
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
import com.racha.ai.core.util.TimeHelper
import com.racha.ai.domain.model.WeatherInfo

@Composable
fun WeatherBanner(weather: WeatherInfo?, modifier: Modifier = Modifier) {
    if (weather == null) return

    val isNight = TimeHelper.isNightTime()
    
    val gradientColors = if (isNight) {
        listOf(Color(0xFF2E1A47), Color(0xFF4A235A), Color(0xFF1A1A3E))
    } else {
        listOf(Color(0xFFE6D5F0), Color(0xFFD4A5D4), Color(0xFFB87DB8))
    }
    
    val weatherIcon = if (isNight) "🌙" else "☀️"
    val timeGreeting = if (isNight) "مساء الخير 🌃" else "صباح الخير 🌅"

    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = Brush.linearGradient(colors = gradientColors))
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = timeGreeting, fontSize = 16.sp, color = if (isNight) Color(0xFFE0D0FF) else Color(0xFF4A235A), fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = weather.cityName ?: "موقعك الحالي", fontSize = 20.sp, color = if (isNight) Color.White else Color(0xFF2E1A47), fontWeight = FontWeight.Bold)
                    }
                    Text(text = weatherIcon, fontSize = 48.sp)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(text = "${weather.temperature}°C", fontSize = 48.sp, color = if (isNight) Color.White else Color(0xFF2E1A47), fontWeight = FontWeight.Bold)
                        // ✅ الإصلاح هنا: استخدام description بدلاً من condition
                        Text(text = weather.description ?: "حالة الطقس معتدلة", fontSize = 16.sp, color = if (isNight) Color(0xFFC0B0FF) else Color(0xFF6B4E9A), fontWeight = FontWeight.Medium)
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "💧", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "${weather.humidity}%", fontSize = 14.sp, color = if (isNight) Color(0xFFD0C0FF) else Color(0xFF8B6FD9))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "💨", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "${weather.windSpeed} كم/س", fontSize = 14.sp, color = if (isNight) Color(0xFFD0C0FF) else Color(0xFF8B6FD9))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = TimeHelper.getCurrentTimeString(), fontSize = 12.sp, color = if (isNight) Color(0xFFB0A0FF) else Color(0xFFA67FB8))
                    }
                }
            }
        }
    }
}
