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
import com.racha.ai.services.prayer.PrayerTimesCalculator
import java.util.*
import java.util.concurrent.TimeUnit

data class PrayerInfo(val name: String, val timeStr: String)
data class PrayerStatus(val next: PrayerInfo, val last: PrayerInfo)

@Composable
fun WeatherBannerWithPrayerTimes(weather: WeatherInfo?, modifier: Modifier = Modifier) {
    if (weather == null) return

    val isDarkTheme = MaterialTheme.colorScheme.background == Color(0xFF0A0A0F)
    
    val prayerStatus = remember(weather.cityName) {
        val times = PrayerTimesCalculator.calculate(
            latitude = 35.8256,
            longitude = 10.6369,
            locationName = weather.cityName ?: "موقعك"
        )
        calculatePrayerStatus(times)
    }
    
    val gradientColors = if (isDarkTheme) {
        listOf(Color(0xFF1A1A2E), Color(0xFF2D2D44), Color(0xFF16213E))
    } else {
        listOf(Color(0xFF4A90E2), Color(0xFF7BB3E8), Color(0xFFA8D0E6))
    }
    
    val weatherIcon = if (isDarkTheme) "🌙" else "☀️"
    val timeGreeting = if (isDarkTheme) "مساء الخير 🌃" else "صباح الخير ☀️"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = Brush.linearGradient(colors = gradientColors))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = timeGreeting, fontSize = 14.sp, color = if (isDarkTheme) Color(0xFFE0E0FF) else Color.White, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = weather.cityName ?: "موقعك", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text(text = weatherIcon, fontSize = 36.sp)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "${weather.temperature}°C", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "💧", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "${weather.humidity}%", fontSize = 13.sp, color = Color(0xFFE0E0FF))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "💨", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "${weather.windSpeed} كم/س", fontSize = 13.sp, color = Color(0xFFE0E0FF))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color.White.copy(alpha = 0.3f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "✓ الأخيرة", fontSize = 11.sp, color = Color(0xFFE0E0FF), fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = prayerStatus.last.name, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(text = prayerStatus.last.timeStr, fontSize = 12.sp, color = Color(0xFFE0E0FF))
                        }
                    }
                    
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "⏳ القادمة", fontSize = 11.sp, color = Color(0xFFE0E0FF), fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = prayerStatus.next.name, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(text = prayerStatus.next.timeStr, fontSize = 12.sp, color = Color(0xFFE0E0FF))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = TimeHelper.getCurrentTimeString(), fontSize = 11.sp, color = Color(0xFFE0E0FF).copy(alpha = 0.7f), modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

private fun calculatePrayerStatus(times: com.racha.ai.domain.model.PrayerTimes): PrayerStatus {
    val now = Date()
    val calendar = Calendar.getInstance()
    
    val prayers = listOf(
        "الفجر" to times.fajr,
        "الظهر" to times.dhuhr,
        "العصر" to times.asr,
        "المغرب" to times.maghrib,
        "العشاء" to times.isha
    )
    
    var nextPrayer = prayers.first()
    var lastPrayer = prayers.last()
    var isNextPrayerTomorrow = false
    
    for (i in prayers.indices) {
        if (prayers[i].second.after(now)) {
            nextPrayer = prayers[i]
            lastPrayer = if (i > 0) prayers[i - 1] else prayers.last()
            break
        }
    }
    
    if (nextPrayer.first == "الفجر") {
        val ishaTime = times.isha
        if (now.after(ishaTime)) {
            isNextPrayerTomorrow = true
            calendar.time = nextPrayer.second
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
    
    val nextTime = if (isNextPrayerTomorrow) calendar.time else nextPrayer.second
    
    val diffNextMillis = nextTime.time - now.time
    val diffLastMillis = now.time - lastPrayer.second.time
    
    val nextH = TimeUnit.MILLISECONDS.toHours(diffNextMillis)
    val nextM = TimeUnit.MILLISECONDS.toMinutes(diffNextMillis) % 60
    val lastH = TimeUnit.MILLISECONDS.toHours(diffLastMillis)
    val lastM = TimeUnit.MILLISECONDS.toMinutes(diffLastMillis) % 60
    
    return PrayerStatus(
        next = PrayerInfo(nextPrayer.first, "بعد ${nextH}س و ${nextM}د"),
        last = PrayerInfo(lastPrayer.first, "منذ ${lastH}س و ${lastM}د")
    )
}
