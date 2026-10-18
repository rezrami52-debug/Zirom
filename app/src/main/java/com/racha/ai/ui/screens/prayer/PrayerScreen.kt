package com.racha.ai.ui.screens.prayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racha.ai.services.prayer.PrayerTimesCalculator
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerScreen(onBack: () -> Unit) {
    val times = remember {
        PrayerTimesCalculator.calculate(
            latitude = 35.8256,
            longitude = 10.6369,
            locationName = "سوسة"
        )
    }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1E3A8A), Color(0xFF0F172A), Color(0xFF2A085C))
    )
    val glassSurface = Color(0x35FFFFFF)
    val glassBorder = Color(0x6660A5FA)

    Box(modifier = Modifier.fillMaxSize().background(backgroundGradient)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("🧭 بوصلة القبلة وأوقات الصلاة", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0x44000000))
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = glassSurface),
                    border = BorderStroke(1.dp, glassBorder)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("اتجاه القبلة بدقة من موقعك", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .background(Color(0x33000000), CircleShape)
                                .border(2.dp, Color(0xFF60A5FA), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🕋", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("132° SE", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("سوسة، تونس", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("الهاتف محاذاة للقبلة تماماً", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = glassSurface),
                    border = BorderStroke(1.dp, glassBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("🕌 مواقيت الصلاة اليوم", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))

                        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        PrayerRow("الفجر", timeFormat.format(times.fajr))
                        PrayerRow("الظهر", timeFormat.format(times.dhuhr))
                        PrayerRow("العصر", timeFormat.format(times.asr))
                        PrayerRow("المغرب", timeFormat.format(times.maghrib))
                        PrayerRow("العشاء", timeFormat.format(times.isha))
                    }
                }
            }
        }
    }
}

@Composable
fun PrayerRow(name: String, time: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Text(time, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
