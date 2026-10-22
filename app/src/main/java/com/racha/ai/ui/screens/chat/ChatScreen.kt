package com.racha.ai.ui.screens.chat

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.racha.ai.R
import com.racha.ai.core.tools.PhotoCaptureActivity
import com.racha.ai.core.voice.VoiceInputHelper
import com.racha.ai.data.repository.NotificationRepository
import com.racha.ai.domain.model.Message
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenPrayer: () -> Unit = {},
    onOpenRoutine: () -> Unit = {},
    viewModel: ChatViewModel = viewModel(),
    weatherViewModel: WeatherViewModel = viewModel()
) {
    val isDarkTheme = isSystemInDarkTheme()
    val state by viewModel.uiState.collectAsState()
    val weatherState by weatherViewModel.uiState.collectAsState()
    val unreadCount by NotificationRepository.unreadCount.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    var isListening by remember { mutableStateOf(false) }
    val voiceHelper = remember { VoiceInputHelper(context) }

    val bgGradient = if (isDarkTheme) {
        Brush.verticalGradient(listOf(Color(0xFF0F0F13), Color(0xFF1A1A24), Color(0xFF0F0F13)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB), Color(0xFFE3F2FD)))
    }
    val glassColor = if (isDarkTheme) Color(0xFF2A2A35).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.7f)
    val glassBorder = if (isDarkTheme) Color(0xFFBD4CFF).copy(alpha = 0.5f) else Color(0xFF0EA5E9).copy(alpha = 0.3f)
    val primaryGlow = if (isDarkTheme) Color(0xFFBD4CFF) else Color(0xFF0EA5E9)
    val userBubbleColor = if (isDarkTheme) Color(0xFF4A2A6B).copy(alpha = 0.8f) else Color(0xFF0EA5E9)
    val aiBubbleColor = if (isDarkTheme) Color(0xFF2A2A35).copy(alpha = 0.9f) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color(0xFF0A1B3A)

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.onSpecialToolCompleted(true, "📸 تم حفظ الصورة")
        else viewModel.onSpecialToolCancelled()
    }

    LaunchedEffect(state.pendingSpecialTool) {
        if (state.pendingSpecialTool == "open_camera") cameraLauncher.launch(Intent(context, PhotoCaptureActivity::class.java))
    }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Box(modifier = Modifier.fillMaxSize().background(bgGradient)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ✅ أزرار EVE
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                val ctx = LocalContext.current
                TextButton(onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(ctx)) {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))
                        ctx.startActivity(intent)
                    } else {
                        ctx.startService(android.content.Intent(ctx, com.racha.ai.core.eve.EveBubbleService::class.java))
                    }
                }) { Text("🫧 تشغيل EVE", fontSize = 12.sp, color = primaryGlow) }
                TextButton(onClick = { com.racha.ai.core.eve.EveState.addNotification() }) { Text("🔔 إشعار", fontSize = 12.sp, color = primaryGlow) }
                TextButton(onClick = {
                    // رابط فيديو تجريبي (Big Buck Bunny)
                    val testVideoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                    com.racha.ai.core.eve.EveState.startMedia(testVideoUrl, video = true)
                }) { Text("▶️ فيديو", fontSize = 12.sp, color = primaryGlow) }
            }

            TopBarRacha(isDarkTheme, primaryGlow, glassColor, glassBorder)

            val currentWeather = weatherState.weather
            if (currentWeather != null) {
                WeatherCardRacha(currentWeather, isDarkTheme, glassColor, glassBorder, primaryGlow)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.messages) { msg ->
                    MessageBubbleRacha(msg, isDarkTheme, userBubbleColor, aiBubbleColor, textColor, primaryGlow)
                }
            }

            InputBarRacha(
                state.inputText,
                viewModel::onInputChange,
                {
                    if (state.inputText.isNotBlank()) viewModel.sendMessage()
                    else if (micPermission.status.isGranted) {
                        isListening = true
                        voiceHelper.startListening(onResult = { viewModel.onInputChange(it); isListening = false }, onError = { isListening = false })
                    } else {
                        micPermission.launchPermissionRequest()
                    }
                },
                isListening,
                isDarkTheme, glassColor, glassBorder, primaryGlow, textColor
            )

            BottomNavRacha(isDarkTheme, primaryGlow, glassColor, unreadCount, onOpenPrayer, onOpenNotifications, onOpenSettings)
        }
    }
}

// ---------------- المكونات الفرعية ----------------

@Composable
fun TopBarRacha(isDark: Boolean, glow: Color, glass: Color, border: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(glass)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(glow.copy(alpha = 0.2f))
                        .border(2.dp, glow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_racha_avatar),
                        contentDescription = "EVE Avatar",
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("Racha AI", color = if (isDark) Color.White else Color(0xFF0A1B3A), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WeatherCardRacha(weather: com.racha.ai.domain.model.WeatherInfo, isDark: Boolean, glass: Color, border: Color, glow: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp) // ✅ تقليل الهوامش
            .clip(RoundedCornerShape(16.dp))
            .background(glass)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .padding(12.dp) // ✅ تقليل الحشو الداخلي
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("مساء الخير", color = if (isDark) Color.White else Color(0xFF0A1B3A), fontSize = 14.sp, fontWeight = FontWeight.Bold) // ✅ تصغير الخط
                Text(weather.cityName ?: "موقعك", color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray, fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${weather.temperature}°C", color = if (isDark) Color.White else Color(0xFF0A1B3A), fontSize = 28.sp, fontWeight = FontWeight.Light) // ✅ تصغير درجة الحرارة
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("💧 ${weather.humidity}%", color = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Gray, fontSize = 11.sp)
                    Text("💨 ${weather.windSpeed} كم/س", color = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Gray, fontSize = 11.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // ✅ أوقات الصلاة (بيانات تجريبية - يمكن ربطها بـ API لاحقاً)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PrayerTimeItem("الفجر", "05:12", isDark, glow)
            PrayerTimeItem("الظهر", "12:30", isDark, glow)
            PrayerTimeItem("العصر", "15:45", isDark, glow)
            PrayerTimeItem("المغرب", "18:20", isDark, glow)
            PrayerTimeItem("العشاء", "19:50", isDark, glow)
        }
    }
}

@Composable
fun PrayerTimeItem(name: String, time: String, isDark: Boolean, glow: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, fontSize = 10.sp, color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray)
        Text(time, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF0A1B3A))
    }
}

@Composable
fun MessageBubbleRacha(message: Message, isDark: Boolean, userColor: Color, aiColor: Color, textColor: Color, glow: Color) {
    val isUser = message.isFromUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (isUser) userColor else aiColor)
                .border(
                    width = 1.dp,
                    color = if (isUser) glow.copy(alpha = 0.3f) else Color.Transparent,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message.text,
                color = if (isUser) Color.White else (if (isDark) Color.White else Color.Black),
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun InputBarRacha(
    text: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isListening: Boolean,
    isDark: Boolean,
    glass: Color,
    border: Color,
    glow: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(30.dp))
                .background(glass)
                .border(1.dp, border, RoundedCornerShape(30.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = if (isDark) Color.White else Color.Black, fontSize = 16.sp),
                cursorBrush = SolidColor(glow),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text("اكتب رسالتك...", color = Color.Gray, fontSize = 16.sp)
                    }
                    innerTextField()
                }
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(52.dp)
                .shadow(12.dp, CircleShape, spotColor = glow, ambientColor = glow)
                .clip(CircleShape)
                .background(glass)
                .border(1.5.dp, glow, CircleShape)
                .clickable { onSend() },
            contentAlignment = Alignment.Center
        ) {
            if (isListening) {
                CircularProgressIndicator(color = glow, modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    imageVector = if (text.isNotBlank()) Icons.Default.Send else Icons.Default.Mic,
                    contentDescription = "send/mic",
                    tint = glow,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
fun BottomNavRacha(
    isDark: Boolean,
    glow: Color,
    glass: Color,
    unreadCount: Int,
    onOpenPrayer: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(glass)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onOpenPrayer() }) {
            Icon(Icons.Default.Mosque, contentDescription = "القبلة", tint = glow, modifier = Modifier.size(24.dp))
            Text("القبلة", fontSize = 10.sp, color = glow)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onOpenNotifications() }) {
            Box {
                Icon(Icons.Default.Notifications, contentDescription = "الإشعارات", tint = glow, modifier = Modifier.size(24.dp))
                if (unreadCount > 0) {
                    Text(text = unreadCount.toString(), modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).background(Color.Red, CircleShape).padding(horizontal = 4.dp), color = Color.White, fontSize = 8.sp)
                }
            }
            Text("الإشعارات", fontSize = 10.sp, color = glow)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onOpenSettings() }) {
            Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = glow, modifier = Modifier.size(24.dp))
            Text("الإعدادات", fontSize = 10.sp, color = glow)
        }
    }
}
