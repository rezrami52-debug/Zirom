package com.racha.ai.ui.screens.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import com.racha.ai.theme.AppThemeManager
import com.racha.ai.R
import com.racha.ai.core.tools.PhotoCaptureActivity
import com.racha.ai.core.voice.VoiceInputHelper
import com.racha.ai.data.repository.NotificationRepository
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenPrayer: () -> Unit = {},
    onOpenRoutine: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
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

    // محرك تحويل النص إلى صوت (TTS)
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    DisposableEffect(Unit) {
        val ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ar")
            }
        }
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    val themeManager = remember { AppThemeManager(context) }
    val themeState by themeManager.themeState.collectAsState()

    val primaryGlow = themeState.glowColor
    val userBubbleColor = themeState.userBubbleColor.copy(alpha = themeState.elementsAlpha)
    val aiBubbleColor = themeState.rachaBubbleColor.copy(alpha = themeState.elementsAlpha)

    val cardBackground = if (isDarkTheme) Color(0xFF0F172A).copy(alpha = 0.88f) else Color(0xFF1E293B).copy(alpha = 0.85f)
    val cardBorder = Brush.linearGradient(
        listOf(primaryGlow.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f))
    )

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.onSpecialToolCompleted(true, "📸 تم التقاط الصورة")
        else viewModel.onSpecialToolCancelled()
    }

    LaunchedEffect(state.pendingSpecialTool) {
        if (state.pendingSpecialTool == "open_camera") cameraLauncher.launch(Intent(context, PhotoCaptureActivity::class.java))
    }

    // التمرير التلقائي اللحظي مع كل حرف جديد يكتب في البث المباشر
    val lastMessageText = state.messages.lastOrNull()?.text.orEmpty()
    LaunchedEffect(state.messages.size, lastMessageText) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
    ) {
        // 1. أزرار EVE العلوية
        QuickActionBarRacha(primaryGlow)

        // 2. الهيدر العلوي مع زري المحادثات السابقة والجديدة
        TopBarRacha(
            glow = primaryGlow,
            onOpenHistory = onOpenHistory,
            onNewChat = { viewModel.startNewChat() }
        )

        // 3. بطاقة الطقس ومواقيت الصلاة
        val currentWeather = weatherState.weather
        if (currentWeather != null) {
            WeatherCardRacha(currentWeather, cardBackground, cardBorder, primaryGlow)
        }

        // 4. منطقة الرسائل مع دعم النطق الصوتي والنسخ
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.messages) { msg ->
                MessageBubbleRacha(
                    message = msg,
                    isDark = isDarkTheme,
                    userColor = userBubbleColor,
                    aiColor = aiBubbleColor,
                    glow = primaryGlow,
                    onSpeak = { text ->
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "RACHA_MSG")
                    },
                    onCopy = { text ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Racha AI", text))
                        Toast.makeText(context, "تم نسخ النص", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (state.isLoading) {
                item {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = primaryGlow
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("رشا تفكر...", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }

        // 5. حقل الإدخال
        InputBarRacha(
            text = state.inputText,
            onValueChange = viewModel::onInputChange,
            onSend = {
                if (state.inputText.isNotBlank()) viewModel.sendMessage()
                else if (micPermission.status.isGranted) {
                    isListening = true
                    voiceHelper.startListening(
                        onResult = { viewModel.onInputChange(it); isListening = false },
                        onError = { isListening = false }
                    )
                } else {
                    micPermission.launchPermissionRequest()
                }
            },
            isListening = isListening,
            isDark = isDarkTheme,
            glow = primaryGlow
        )

        // 6. الشريط السفلي العائم
        BottomNavRacha(
            glow = primaryGlow,
            unreadCount = unreadCount,
            onOpenPrayer = onOpenPrayer,
            onOpenNotifications = onOpenNotifications,
            onOpenSettings = onOpenSettings
        )
    }
}

@Composable
fun TopBarRacha(
    glow: Color,
    onOpenHistory: () -> Unit,
    onNewChat: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.75f),
        border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(glow.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f))))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(glow.copy(alpha = 0.35f), Color.Transparent)))
                        .border(2.dp, glow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_racha_avatar),
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Racha AI", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("المساعد التكتيكي النشط", color = glow.copy(alpha = 0.85f), fontSize = 10.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNewChat, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "محادثة جديدة", tint = glow)
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onOpenHistory, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.History, contentDescription = "المحادثات السابقة", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun QuickActionBarRacha(glow: Color) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickActionButton("🫧 تشغيل EVE", glow) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(ctx)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                ctx.startActivity(intent)
            } else {
                ctx.startService(Intent(ctx, com.racha.ai.core.eve.EveBubbleService::class.java))
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        QuickActionButton("🔔 إشعار", glow) {
            com.racha.ai.core.eve.EveState.addNotification()
        }
        Spacer(modifier = Modifier.width(6.dp))
        QuickActionButton("▶️ فيديو", glow) {
            val intent = Intent(ctx, com.racha.ai.core.media.FloatingMediaService::class.java).apply {
                putExtra(com.racha.ai.core.media.FloatingMediaService.EXTRA_MEDIA_URI, "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                putExtra(com.racha.ai.core.media.FloatingMediaService.EXTRA_MEDIA_TITLE, "Big Buck Bunny")
            }
            ctx.startService(intent)
        }
    }
}

@Composable
fun QuickActionButton(text: String, glow: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.65f),
        border = BorderStroke(1.dp, glow.copy(alpha = 0.35f)),
        modifier = Modifier.height(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = glow)
        }
    }
}

@Composable
fun WeatherCardRacha(
    weather: com.racha.ai.domain.model.WeatherInfo,
    cardBg: Color,
    cardBorder: Brush,
    glow: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("مساء الخير 🌤️", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(weather.cityName ?: "موقعك الحالي", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${weather.temperature}°C", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("💧 ${weather.humidity}%", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text("💨 ${weather.windSpeed} كم/س", color = Color(0xFFE2E8F0), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PrayerTimeItem("الفجر", "05:12", glow)
                PrayerTimeItem("الظهر", "12:30", glow)
                PrayerTimeItem("العصر", "15:45", glow)
                PrayerTimeItem("المغرب", "18:20", glow)
                PrayerTimeItem("العشاء", "19:50", glow)
            }
        }
    }
}

@Composable
fun PrayerTimeItem(name: String, time: String, glow: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, fontSize = 10.sp, color = Color(0xFF94A3B8))
        Spacer(modifier = Modifier.height(2.dp))
        Text(time, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = glow)
    }
}

@Composable
fun MessageBubbleRacha(
    message: ChatMessage,
    isDark: Boolean,
    userColor: Color,
    aiColor: Color,
    glow: Color,
    onSpeak: (String) -> Unit = {},
    onCopy: (String) -> Unit = {}
) {
    val isUser = message.isFromUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) userColor else Color(0xFF1E293B).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, if (isUser) glow.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f)),
            shadowElevation = 4.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )

                // أزرار سريعة فقط لرسائل الذكاء الاصطناعي (نسخ + قراءة صوتية)
                if (!isUser && message.text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onCopy(message.text) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = Color.LightGray, modifier = Modifier.size(15.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { onSpeak(message.text) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "استماع", tint = glow, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
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
    glow: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.88f),
            border = BorderStroke(1.dp, glow.copy(alpha = 0.4f))
        ) {
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                BasicTextField(
                    value = text,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(glow),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text("اكتب رسالتك لـ Racha...", color = Color(0xFF64748B), fontSize = 15.sp)
                        }
                        innerTextField()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            onClick = onSend,
            shape = CircleShape,
            color = Color(0xFF0F172A).copy(alpha = 0.9f),
            border = BorderStroke(1.5.dp, glow),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isListening) {
                    CircularProgressIndicator(color = glow, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = if (text.isNotBlank()) Icons.Default.Send else Icons.Default.Mic,
                        contentDescription = "Send",
                        tint = glow,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavRacha(
    glow: Color,
    unreadCount: Int,
    onOpenPrayer: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.15f), glow.copy(alpha = 0.4f), Color.White.copy(alpha = 0.15f)))),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(Icons.Default.Mosque, "القبلة", glow, onOpenPrayer)

            Box(contentAlignment = Alignment.Center) {
                NavItem(Icons.Default.Notifications, "الإشعارات", glow, onOpenNotifications)
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-2).dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            NavItem(Icons.Default.Settings, "الإعدادات", glow, onOpenSettings)
        }
    }
}

@Composable
fun NavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, glow: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = glow, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
    }
}
