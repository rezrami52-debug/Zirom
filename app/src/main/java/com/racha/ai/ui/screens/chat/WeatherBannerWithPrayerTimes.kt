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

    val isNight = TimeHelper.isNightTime()
    
    val prayerStatus = remember(weather.cityName) {
        val times = PrayerTimesCalculator.calculate(
            latitude = 35.8256,
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
    
    val weatherIcon = if (isNight) "" else "☀️"
    val timeGreeting = if (isNight) "مساء الخير 🌃" else "صباح الخير "

    // ✅ بانر أقصر وأكثر إحكاما
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = Brush.linearGradient(colors = gradientColors))
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // الصف 1: التحية والموقع والأيقونة
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = timeGreeting, fontSize = 12.sp, color = if (isNight) Color(0xFFE0D0FF) else Color(0xFF4A235A), fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = weather.cityName ?: "موقعك", fontSize = 16.sp, color = if (isNight) Color.White else Color(0xFF2E1A47), fontWeight = FontWeight.Bold)
                    }
                    Text(text = weatherIcon, fontSize = 32.sp)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // الصف 2: الحرارة والتفاصيل
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "${weather.temperature}°C", fontSize = 36.sp, color = if (isNight) Color.White else Color(0xFF2E1A47), fontWeight = FontWeight.Bold)
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "💧", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = "${weather.humidity}%", fontSize = 11.sp, color = if (isNight) Color(0xFFD0C0FF) else Color(0xFF8B6FD9))
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = "${weather.windSpeed} كم/س", fontSize = 11.sp, color = if (isNight) Color(0xFFD0C0FF) else Color(0xFF8B6FD9))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = if (isNight) Color.White.copy(alpha = 0.3f) else Color(0xFF2E1A47).copy(alpha = 0.2f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(6.dp))
                
                // الصف 3: مواقيت الصلاة
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "⏳ القادمة", fontSize = 10.sp, color = if (isNight) Color(0xFFB0A0FF) else Color(0xFF8B6FD9), fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(text = prayerStatus.next.name, fontSize = 14.sp, color = if (isNight) Color.White else Color(0xFF2E1A47), fontWeight = FontWeight.Bold)
                        Text(text = prayerStatus.next.timeStr, fontSize = 11.sp, color = if (isNight) Color(0xFFD0C0FF) else Color(0xFF6B4E9A))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "✓ الأخيرة", fontSize = 10.sp, color = if (isNight) Color(0xFFB0A0FF) else Color(0xFF8B6FD9), fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(text = prayerStatus.last.name, fontSize = 14.sp, color = if (isNight) Color.White else Color(0xFF2E1A47), fontWeight = FontWeight.Bold)
                        Text(text = prayerStatus.last.timeStr, fontSize = 11.sp, color = if (isNight) Color(0xFFD0C0FF) else Color(0xFF6B4E9A))
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = TimeHelper.getCurrentTimeString(), fontSize = 10.sp, color = if (isNight) Color(0xFF9080FF) else Color(0xFFA67FB8), modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

// ✅ حساب دقيق مع معالجة خاصة لصلاة الفجر
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
    
    // البحث عن الصلاة القادمة
    for (i in prayers.indices) {
        if (prayers[i].second.after(now)) {
            nextPrayer = prayers[i]
            lastPrayer = if (i > 0) prayers[i - 1] else prayers.last()
            break
        }
    }
    
    // ✅ معالجة خاصة للفجر: إذا كنا بعد العشاء، الفجر في الغد
    if (nextPrayer.first == "الفجر") {
        val ishaTime = times.isha
        if (now.after(ishaTime)) {
            isNextPrayerTomorrow = true
            // إضافة يوم كامل للحساب
            calendar.time = nextPrayer.second
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
    
    // حساب الفرق الزمني
    val nextTime = if (isNextPrayerTomorrow) {
        calendar.time
    } else {
        nextPrayer.second
    }
    
    val diffNextMillis = nextTime.time - now.time
    val diffLastMillis = now.time - lastPrayer.second.time
    
    val nextH = TimeUnit.MILLISECONDS.toHours(diffNextMillis)
    val nextM = TimeUnit.MILLISECONDS.toMinutes(diffNextMillis) % 60
    val lastH = TimeUnit.MILLISECONDS.toHours(diffLastMillis)
    val lastM = TimeUnit.MILLISECONDS.toMinutes(diffLastMillis) % 60
    
    return PrayerStatus(
        next = PrayerInfo(nextPrayer.first, "متبقي ${nextH}س و ${nextM}د"),
        last = PrayerInfo(lastPrayer.first, "منذ ${lastH}س و ${lastM}د")
    )
}
