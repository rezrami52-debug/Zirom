package com.racha.ai.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.racha.ai.domain.model.WeatherInfo
import com.racha.ai.services.prayer.PrayerTimesCalculator
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class PrayerInfo(val name: String, val timeStr: String)
data class PrayerStatus(val next: PrayerInfo, val last: PrayerInfo)

@Composable
fun WeatherBannerWithPrayerTimes(
    weather: WeatherInfo?,
    onOpenPrayer: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // القسم الأول: البوصلة القابلة للنقر لتفتح القبلة والصلاة
            Row(
                modifier = Modifier.clickable { onOpenPrayer() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0x44000000), CircleShape)
                        .border(1.dp, Color(0xFF60A5FA), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🧭", fontSize = 12.sp)
                        Text(text = "132°", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (currentTime.isNotEmpty()) currentTime else "05:16 PM",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "القبلة 🕋",
                        fontSize = 10.sp,
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            )

            // القسم الثاني: الطقس
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${weather.temperature}°C",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = weather.cityName ?: "سوسة",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            )

            // القسم الثالث: الصلاة الدقيقة (القادمة)
            Column(
                modifier = Modifier.clickable { onOpenPrayer() },
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "🕌 ${prayerStatus.next.name}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFC107)
                )
                Text(
                    text = prayerStatus.next.timeStr,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

private fun calculatePrayerStatus(times: com.racha.ai.domain.model.PrayerTimes): PrayerStatus {
    val now = Date()

    val prayers = listOf(
        "الفجر" to times.fajr,
        "الظهر" to times.dhuhr,
        "العصر" to times.asr,
        "المغرب" to times.maghrib,
        "العشاء" to times.isha
    )

    var nextPair = prayers.first()
    var lastPair = prayers.last()

    for (i in prayers.indices) {
        if (prayers[i].second.after(now)) {
            nextPair = prayers[i]
            lastPair = if (i > 0) prayers[i - 1] else prayers.last()
            break
        }
    }

    val diffNextMillis = Math.abs(nextPair.second.time - now.time)
    val diffLastMillis = Math.abs(now.time - lastPair.second.time)

    val nextH = TimeUnit.MILLISECONDS.toHours(diffNextMillis)
    val nextM = TimeUnit.MILLISECONDS.toMinutes(diffNextMillis) % 60
    val lastH = TimeUnit.MILLISECONDS.toHours(diffLastMillis)
    val lastM = TimeUnit.MILLISECONDS.toMinutes(diffLastMillis) % 60

    val nextStr = if (nextH > 0) "بعد ${nextH}س و ${nextM}د" else "بعد ${nextM}د"
    val lastStr = if (lastH > 0) "منذ ${lastH}س و ${lastM}د" else "منذ ${lastM}د"

    return PrayerStatus(
        next = PrayerInfo(nextPair.first, nextStr),
        last = PrayerInfo(lastPair.first, lastStr)
    )
}
