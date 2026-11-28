package com.racha.ai.ui.screens.prayer

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racha.ai.services.prayer.PrayerTimesCalculator
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var azimuth by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        var gravity: FloatArray? = null
        var geomagnetic: FloatArray? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) gravity = event.values
                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) geomagnetic = event.values

                if (gravity != null && geomagnetic != null) {
                    val R = FloatArray(9)
                    val I = FloatArray(9)
                    if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(R, orientation)
                        azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)

        onDispose { sensorManager.unregisterListener(listener) }
    }

    val animatedRotation by animateFloatAsState(targetValue = -azimuth, label = "compass")

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
                    title = { Text("🧭 البوصلة المباشرة واتجاه القبلة", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
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
                        Text("القبلة: 132° SE (سوسة، تونس)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .background(Color(0x33000000), CircleShape)
                                .border(2.dp, Color(0xFF60A5FA), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "بوصلة",
                                tint = Color(0xFFFFC107),
                                modifier = Modifier
                                    .size(70.dp)
                                    .rotate(animatedRotation + 132f)
                            )
                            Text("🕋", fontSize = 22.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("درجة الاتجاه الحالي: ${azimuth.toInt()}°", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
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
