package com.racha.ai.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class PrayerInfo(val name: String, val timeStr: String)
data class PrayerStatus(val next: PrayerInfo, val last: PrayerInfo)

@Composable
fun WeatherBannerWithPrayerTimes(weather: WeatherInfo?, modifier: Modifier = Modifier) {
    if (weather == null) return

    val prayerStatus = remember(weather.cityName) {
        val times = PrayerTimesCalculator.calculate(
            latitude = 35.8256,
            longitude = 10.6369,
            locationName = weather.cityName ?: "سوسة"
        )
        calculatePrayerStatus(times)
    }

    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    // خلفية زجاجية مضيئة ومدمجة بحجم صغير
    val glassBg = listOf(Color(0x33FFFFFF), Color(0x1160A5FA))
    val glassBorder = Color(0x44FFFFFF)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(glassBg))
                .border(1.dp, glassBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // --- القسم الأول: البوصلة والساعة الحالية مع القبلة ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0x33000000), CircleShape)
                        .border(1.dp, Color(0xFF60A5FA), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🧭", fontSize = 14.sp)
                        Text(text = "🕋 132°", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (currentTime.isNotEmpty()) currentTime else "08:20 PM",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "اتجاه القبلة",
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // فاصل عمودي شفاف
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            )

            // --- القسم الثاني: الطقس المدمج ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${weather.temperature}°C  💧86%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = weather.cityName ?: "سوسة",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            // فاصل عمودي شفاف
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            )

            // --- القسم الثالث: الصلاة القادمة ---
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "🕌 ${prayerStatus.next.name}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFC107)
                )
                Text(
                    text = prayerStatus.next.timeStr,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
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
