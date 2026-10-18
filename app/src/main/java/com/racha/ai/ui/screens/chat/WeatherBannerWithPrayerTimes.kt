package com.racha.ai.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

    val colors = MaterialTheme.colorScheme
    val isNight = TimeHelper.isNightTime()

    val prayerStatus = remember(weather.cityName) {
        val times = PrayerTimesCalculator.calculate(
            latitude = 35.8256,
            longitude = 10.6369,
            locationName = weather.cityName ?: "موقعك"
        )
        calculatePrayerStatus(times)
    }

    // تدرج خلفية البانر الرئيسي حسب التوقيت (ليلي / نهاري)
    val mainCardGradient = if (isNight) {
        listOf(Color(0xFF2D2638).copy(alpha = 0.85f), Color(0xFF1E1B26).copy(alpha = 0.95f))
    } else {
        listOf(Color(0xFF1E3A8A).copy(alpha = 0.85f), Color(0xFF0F172A).copy(alpha = 0.95f))
    }

    // ألوان المربعات الداخلية والتفاصيل الشفافة (Glassmorphism)
    val subCardBg = if (isNight) Color(0xFF383047).copy(alpha = 0.5f) else Color(0xFF2563EB).copy(alpha = 0.3f)
    val subCardBorder = if (isNight) Color(0xFFBB86FC).copy(alpha = 0.3f) else Color(0xFF60A5FA).copy(alpha = 0.4f)
    val accentColor = if (isNight) Color(0xFFE0B6FF) else Color(0xFFFFB74D)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(mainCardGradient))
                .border(1.dp, subCardBorder, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            // --- الجزء العلوي: الطقس ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isNight) "مساء الخير" else "صباح الخير",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = weather.cityName ?: "موقعك",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${weather.temperature}°C",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isNight) "🌙 86%" else "💧 86%",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "💨 5.0 كم/س",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // --- الجزء السفلي: مربعا الصلاة (القادمة والأخيرة) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // المربع الأول: الصلاة الأخيرة
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(subCardBg, RoundedCornerShape(14.dp))
                        .border(1.dp, subCardBorder, RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "الأخيرة  ✓",
                            fontSize = 10.sp,
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = prayerStatus.last.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = prayerStatus.last.timeStr,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // المربع الثاني: الصلاة القادمة
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(subCardBg, RoundedCornerShape(14.dp))
                        .border(1.dp, subCardBorder, RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "القادمة  ⏳",
                            fontSize = 10.sp,
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = prayerStatus.next.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = prayerStatus.next.timeStr,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
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
        next = PrayerInfo(nextPrayer.first, "بعد ${nextH}س ${nextM}د"),
        last = PrayerInfo(lastPrayer.first, "منذ ${lastH}س ${lastM}د")
    )
}
