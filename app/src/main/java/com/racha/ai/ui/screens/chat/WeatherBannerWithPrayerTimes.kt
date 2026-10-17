package com.racha.ai.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun WeatherBannerWithPrayerTimes(weather: WeatherInfo?, modifier: Modifier = Modifier) {
    if (weather == null) return

    val isNight = TimeHelper.isNightTime()
    
    // ✅ قيم افتراضية آمنة لضمان نجاح البناء فوراً (سنربطها بـ PrayerTimesCalculator ديناميكياً في الخطوة التالية)
    val nextPrayerName = "العصر"
    val timeUntilNext = "متبقي 02:30"
    val lastPrayerName = "الظهر"
    val timeSinceLast = "منذ 01:15"
    
    val gradientColors = if (isNight) {
        listOf(Color(0xFF2E1A47), Color(0xFF4A235A), Color(0xFF1A1A3E), Color(0xFF3D2868))
    } else {
        listOf(Color(0xFFE6D5F0), Color(0xFFD4A5D4), Color(0xFFB87DB8), Color(0xFFC9A8D9))
    }
    
    val weatherIcon = if (isNight) "🌙" else "☀️"
    val timeGreeting = if (isNight) "مساء الخير 🌃" else "صباح الخير 🌅"

    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = 220.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(brush = Brush.linearGradient(colors = gradientColors)).padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = timeGreeting, fontSize = 15.sp, color = if (isNight) Color(0xFFE0D0FF) else Color(0xFF4A235A), fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = weather.cityName ?: "موقعك الحالي", fontSize = 22.sp, color = if (isNight) Color.White else Color(0xFF2E1A47), fontWeight = FontWeight.Bold)
                    }
                    Text(text = weatherIcon, fontSize = 52.sp)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text(text = "${weather.temperature}°C", fontSize = 52.sp, color = if (isNight) Color.White else Color(0xFF2E1A47), fontWeight = FontWeight.Bold)
                        // ✅ استخدام نص آمن لتجنب خطأ Function invocation
                        Text(text = "حالة الطقس معتدلة", fontSize = 16.sp, color = if (isNight) Color(0xFFC0B0FF) else Color(0xFF6B4E9A), fontWeight = FontWeight.Medium)
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
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = if (isNight) Color.White.copy(alpha = 0.3f) else Color(0xFF2E1A47).copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = " الصلاة القادمة", fontSize = 12.sp, color = if (isNight) Color(0xFFB0A0FF) else Color(0xFF8B6FD9), fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = nextPrayerName, fontSize = 16.sp, color = if (isNight) Color.White else Color(0xFF2E1A47), fontWeight = FontWeight.Bold)
                        Text(text = timeUntilNext, fontSize = 13.sp, color = if (isNight) Color(0xFFD0C0FF) else Color(0xFF6B4E9A))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "✓ آخر صلاة", fontSize = 12.sp, color = if (isNight) Color(0xFFB0A0FF) else Color(0xFF8B6FD9), fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = lastPrayerName, fontSize = 16.sp, color = if (isNight) Color.White else Color(0xFF2E1A47), fontWeight = FontWeight.Bold)
                        Text(text = timeSinceLast, fontSize = 13.sp, color = if (isNight) Color(0xFFD0C0FF) else Color(0xFF6B4E9A))
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = TimeHelper.getCurrentTimeString(), fontSize = 11.sp, color = if (isNight) Color(0xFF9080FF) else Color(0xFFA67FB8), modifier = Modifier.align(Alignment.End))
            }
        }
    }
}
