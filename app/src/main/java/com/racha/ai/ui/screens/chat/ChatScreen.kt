package com.racha.ai.ui.screens.chat

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.racha.ai.core.voice.VoiceInputHelper
import com.racha.ai.domain.model.Message
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPrayer: () -> Unit,
    onOpenRoutine: () -> Unit,
    viewModel: ChatViewModel = viewModel(),
    weatherViewModel: WeatherViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val weatherState by weatherViewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var selectedTab by remember { mutableIntStateOf(0) }
    var isListening by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    val voiceHelper = remember { VoiceInputHelper(context) }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1E3A8A),
            Color(0xFF0F172A),
            Color(0xFF2A085C)
        )
    )
    val glassSurface = Color(0x35FFFFFF)
    val glassBorder = Color(0x6660A5FA)

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK)
            viewModel.onSpecialToolCompleted(true, "📸 تم حفظ الصورة")
        else viewModel.onSpecialToolCancelled()
    }

    LaunchedEffect(state.pendingSpecialTool) {
        if (state.pendingSpecialTool == "open_camera")
            cameraLauncher.launch(Intent(context, com.racha.ai.core.tools.PhotoCaptureActivity::class.java))
    }
    DisposableEffect(Unit) { onDispose { voiceHelper.destroy() } }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Surface(
                modifier = Modifier.fillMaxHeight().width(300.dp),
                color = Color(0xFF0F172A),
                shadowElevation = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("المحادثات السابقة", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        IconButton(onClick = { scope.launch { drawerState.close() } }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.conversations.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("لا توجد محادثات سابقة", color = Color.White.copy(alpha = 0.7f))
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.conversations) { conv ->
                                val isSelected = conv.id == state.currentConversationId
                                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar")).format(Date(conv.timestamp))
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        viewModel.loadConversation(conv.id)
                                        scope.launch { drawerState.close() }
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.4f) else glassSurface
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(conv.title, fontWeight = FontWeight.Bold, maxLines = 2, color = Color.White)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(dateStr, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        content = {
            Box(modifier = Modifier.fillMaxSize().background(backgroundGradient)) {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        TopAppBar(
                            title = { Text("Racha AI", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White) },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "القائمة", tint = Color.White)
                                }
                            },
                            actions = {
                                Text("🤖", fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(0x44000000),
                                titleContentColor = Color.White
                            )
                        )
                    },
                    bottomBar = {
                        BottomAppBar(
                            containerColor = Color(0x660F172A),
                            contentColor = Color.White,
                            tonalElevation = 0.dp
                        ) {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Mosque, "الصلاة", modifier = Modifier.size(24.dp), tint = if (selectedTab == 0) Color(0xFFFFC107) else Color.White.copy(alpha = 0.7f)) },
                                label = { Text("الصلاة", fontSize = 11.sp, color = if (selectedTab == 0) Color(0xFFFFC107) else Color.White.copy(alpha = 0.7f)) },
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0; onOpenPrayer() }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Notifications, "الإشعارات", modifier = Modifier.size(24.dp), tint = if (selectedTab == 1) Color(0xFFFFC107) else Color.White.copy(alpha = 0.7f)) },
                                label = { Text("الإشعارات", fontSize = 11.sp, color = if (selectedTab == 1) Color(0xFFFFC107) else Color.White.copy(alpha = 0.7f)) },
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1; onOpenNotifications() }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Settings, "الإعدادات", modifier = Modifier.size(24.dp), tint = if (selectedTab == 2) Color(0xFFFFC107) else Color.White.copy(alpha = 0.7f)) },
                                label = { Text("الإعدادات", fontSize = 11.sp, color = if (selectedTab == 2) Color(0xFFFFC107) else Color.White.copy(alpha = 0.7f)) },
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2; onOpenSettings() }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Add, "محادثة جديدة", modifier = Modifier.size(24.dp), tint = if (selectedTab == 3) Color(0xFFFFC107) else Color.White.copy(alpha = 0.7f)) },
                                label = { Text("جديدة", fontSize = 11.sp, color = if (selectedTab == 3) Color(0xFFFFC107) else Color.White.copy(alpha = 0.7f)) },
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3; viewModel.startNewChat() }
                            )
                        }
                    }
                ) { paddingValues ->
                    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                        when {
                            weatherState.isLoading -> {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("جاري تحديث الطقس...", color = Color.White, fontSize = 13.sp)
                                }
                            }
                            weatherState.error != null -> {
                                Card(modifier = Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = glassSurface)) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocationOff, null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(weatherState.error ?: "خطأ في الطقس", color = Color.White, fontSize = 13.sp)
                                    }
                                }
                            }
                            weatherState.weather != null -> {
                                WeatherBannerWithPrayerTimes(
                                    weather = weatherState.weather,
                                    onOpenPrayer = onOpenPrayer,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }

                        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.messages) { msg -> MessageBubble(message = msg) }
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = state.inputText,
                                onValueChange = viewModel::onInputChange,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("اكتب رسالتك هنا...", color = Color.White.copy(alpha = 0.6f)) },
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF60A5FA),
                                    unfocusedBorderColor = glassBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedContainerColor = glassSurface,
                                    unfocusedContainerColor = glassSurface
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (state.inputText.isNotBlank()) {
                                        viewModel.sendMessage()
                                    } else if (micPermission.status.isGranted) {
                                        isListening = true
                                        voiceHelper.startListening(
                                            onResult = { viewModel.onInputChange(it); isListening = false },
                                            onError = { voiceError = it; isListening = false }
                                        )
                                    } else { micPermission.launchPermissionRequest() }
                                },
                                modifier = Modifier.background(Color(0xFF3B82F6), RoundedCornerShape(50)).size(48.dp)
                            ) {
                                if (isListening) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                                } else if (state.inputText.isNotBlank()) {
                                    Icon(Icons.Default.Send, "إرسال", tint = Color.White)
                                } else {
                                    Icon(Icons.Default.Mic, "صوت", tint = Color.White)
                                }
                            }
                        }
                        if (voiceError != null) {
                            Text("⚠️ $voiceError", color = Color(0xFFFF6B6B), modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun MessageBubble(message: Message, modifier: Modifier = Modifier) {
    val isUser = message.isFromUser

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) Color(0xFF2563EB).copy(alpha = 0.85f) else Color(0x45FFFFFF),
            modifier = Modifier.widthIn(max = 280.dp).padding(vertical = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(message.text, color = Color.White, fontSize = 14.sp)
                if (message.isError) {
                    Spacer(modifier = Modifier.padding(top = 4.dp))
                    Text("⚠️ حدث خطأ في الاستجابة", color = Color(0xFFFF6B6B), fontSize = 11.sp)
                }
            }
        }
    }
}
