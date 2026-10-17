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
import java.util.Date

data class PrayerInfo(val name: String, val timeStr: String)
data class PrayerStatus(val next: PrayerInfo, val last: PrayerInfo)

@Composable
fun WeatherBannerWithPrayerTimes(weather: WeatherInfo?, modifier: Modifier = Modifier) {
    if (weather == null) return

    val isNight = TimeHelper.isNightTime()
    
    // ✅ حساب دقيق باستخدام PrayerTimesCalculator الموجود في المشروع
    val prayerStatus = remember(weather.cityName) {
        val times = PrayerTimesCalculator.calculate(
            latitude = 35.8256, // إحداثيات سوسة (يمكن تحسينها لاحقاً بالموقع الفعلي)
            longitude = 10.6369,
            locationName = weather.cityName ?: "موقعك"
        )
        calculatePrayerStatus(times)
    }
    
    val gradientColors = if (isNight) {
        listOf(Color(0xFF2E1A47), Color(0xFF4A235A), Color(0xFF1A1A3E))
    } else {
        listOf(Color(0xFFE6D5F0), Color(0xFFD4A5D4), Color(0xFFB87DB8))
    }
    
    val weatherIcon = if (isNight) "🌙" else "☀️"
    val timeGreeting = if (isNight) "مساء الخير 🌃" else "صباح الخير 🌅"

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
                        Text(text = timeGreeting, fontSize = 14.sp, color = if (isNight) Color(0xFFE0D0FF) else Color(0xFF4A235A), fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = weather.cityName ?: "موقعك الحالي", fontSize = 18.sp, color = if (isNight) Color.White else Color(0xFF2E1A47), fontWeight = FontWeight.Bold)
                    }
                    Text(text = weatherIcon, fontSize = 40.sp)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "${weather.temperature}°C", fontSize = 42.sp, color = if (isNight) Color.White else Color(0xFF2E1A47), fontWeight = FontWeight.Bold)
                        Text(text = "حالة الطقس معتدلة", fontSize = 14.sp, color = if (isNight) Color(0xFFC0B0FF) else Color(0xFF6B4E9A), fontWeight = FontWeight.Medium)
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "💧", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "${weather.humidity}%", fontSize = 13.sp, color = if (isNight) Color(0xFFD0C0FF) else Color(0xFF8B6FD9))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "💨", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "${weather.windSpeed} كم/س", fontSize = 13.sp, color = if (isNight) Color(0xFFD0C0FF) else Color(0xFF8B6FD9))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = if (isNight) Color.White.copy(alpha = 0.3f) else Color(0xFF2E1A47).copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "⏳ الصلاة القادمة", fontSize = 11.sp, color = if (isNight) Color(0xFFB0A0FF) else Color(0xFF8B6FD9), fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = prayerStatus.next.name, fontSize = 15.sp, color = if (isNight) Color.White else Color(0xFF2E1A47), fontWeight = FontWeight.Bold)
                        Text(text = prayerStatus.next.timeStr, fontSize = 12.sp, color = if (isNight) Color(0xFFD0C0FF) else Color(0xFF6B4E9A))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "✓ آخر صلاة", fontSize = 11.sp, color = if (isNight) Color(0xFFB0A0FF) else Color(0xFF8B6FD9), fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = prayerStatus.last.name, fontSize = 15.sp, color = if (isNight) Color.White else Color(0xFF2E1A47), fontWeight = FontWeight.Bold)
                        Text(text = prayerStatus.last.timeStr, fontSize = 12.sp, color = if (isNight) Color(0xFFD0C0FF) else Color(0xFF6B4E9A))
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = TimeHelper.getCurrentTimeString(), fontSize = 11.sp, color = if (isNight) Color(0xFF9080FF) else Color(0xFFA67FB8), modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

// ✅ دالة حساب دقيقة تعتمد على RachaPrayerTimes
private fun calculatePrayerStatus(times: com.racha.ai.domain.model.PrayerTimes): PrayerStatus {
    val now = Date()
    val prayers = listOf(
        "الفجر" to times.fajr,
        "الظهر" to times.dhuhr,
        "العصر" to times.asr,
        "المغرب" to times.maghrib,
        "العشاء" to times.isha
    )
    
    var nextPrayer = prayers.first()
    var lastPrayer = prayers.last()
    
    for (i in prayers.indices) {
        if (prayers[i].second.after(now)) {
            nextPrayer = prayers[i]
            lastPrayer = if (i > 0) prayers[i - 1] else prayers.last()
            break
        }
    }
    
    val diffNext = (nextPrayer.second.time - now.time) / (1000 * 60)
    val diffLast = (now.time - lastPrayer.second.time) / (1000 * 60)
    
    val nextH = diffNext / 60
    val nextM = diffNext % 60
    val lastH = diffLast / 60
    val lastM = diffLast % 60
    
    return PrayerStatus(
        next = PrayerInfo(nextPrayer.first, "متبقي ${nextH}س و ${nextM}د"),
        last = PrayerInfo(lastPrayer.first, "منذ ${lastH}س و ${lastM}د")
    )
}
