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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    val context = LocalContext.current
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var selectedTab by remember { mutableIntStateOf(0) }
    var isListening by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    val voiceHelper = remember { VoiceInputHelper(context) }
    
    val primaryColor = if (isDarkTheme) Color(0xFFBD4CFF) else Color(0xFF0EA5E9)
    val backgroundColor = if (isDarkTheme) Color(0xFF0A0A0F) else Color(0xFFE3F2FD)

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.onSpecialToolCompleted(true, "📸 تم حفظ الصورة")
        else viewModel.onSpecialToolCancelled()
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.copyQwenModel(uri)
    }

    LaunchedEffect(state.pendingSpecialTool) {
        if (state.pendingSpecialTool == "open_camera") cameraLauncher.launch(Intent(context, PhotoCaptureActivity::class.java))
    }
    DisposableEffect(Unit) { onDispose { voiceHelper.destroy() } }
    LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1) }
    LaunchedEffect(voiceError) { if (voiceError != null) { kotlinx.coroutines.delay(3000); voiceError = null } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Surface(modifier = Modifier.fillMaxHeight().width(300.dp), color = if (isDarkTheme) Color(0xFF1A1A2E) else Color.White, shadowElevation = 16.dp) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("المحادثات السابقة", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                        IconButton(onClick = { scope.launch { drawerState.close() } }) { Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = primaryColor) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = primaryColor.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.conversations.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد محادثات سابقة", color = if (isDarkTheme) Color.White else Color.Black) }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.conversations) { conv ->
                                val isSelected = conv.id == state.currentConversationId
                                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar")).format(Date(conv.timestamp))
                                Card(modifier = Modifier.fillMaxWidth().clickable { viewModel.loadConversation(conv.id); scope.launch { drawerState.close() } },
                                    colors = CardDefaults.cardColors(containerColor = if (isSelected) primaryColor.copy(alpha = 0.3f) else (if (isDarkTheme) Color(0xFF2D2D44) else Color(0xFFF5F5F5))),
                                    shape = RoundedCornerShape(12.dp)) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(text = conv.title, fontWeight = FontWeight.Bold, maxLines = 2, color = if (isDarkTheme) Color.White else Color.Black)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = dateStr, fontSize = 12.sp, color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Gray)
                                        if (isSelected) { Spacer(modifier = Modifier.height(4.dp)); Text(text = "✓ حالي", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryColor) }
                                    }
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { viewModel.startNewChat(); scope.launch { drawerState.close() } }, modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("محادثة جديدة", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        },
        content = {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    modifier = Modifier.size(42.dp).clip(CircleShape).background(if (isDarkTheme) Color(0xFF1A1A2E) else Color.White).border(2.5.dp, primaryColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(painter = painterResource(id = R.drawable.ic_racha_avatar), contentDescription = "EVE", modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Crop)
                                }
                                Text(text = "Racha AI", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = if (isDarkTheme) Color.White else Color(0xFF0A2E5A))
                            }
                        },
                        navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "القائمة", tint = primaryColor) } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
                    )
                },
                bottomBar = {
                    BottomAppBar(containerColor = backgroundColor, contentColor = primaryColor, tonalElevation = 8.dp) {
                        NavigationBarItem(icon = { Icon(Icons.Default.Mosque, contentDescription = "القبلة", modifier = Modifier.size(24.dp), tint = if (selectedTab == 0) primaryColor else Color.Gray) },
                            label = { Text("القبلة", fontSize = 11.sp, color = if (selectedTab == 0) primaryColor else Color.Gray) },
                            selected = selectedTab == 0, onClick = { selectedTab = 0; onOpenPrayer() })
                        NavigationBarItem(icon = { Icon(Icons.Default.Notifications, contentDescription = "الإشعارات", modifier = Modifier.size(24.dp), tint = if (selectedTab == 1) primaryColor else Color.Gray) },
                            label = { Box { Text("الإشعارات", fontSize = 11.sp, color = if (selectedTab == 1) primaryColor else Color.Gray); if (unreadCount > 0) Text(text = unreadCount.toString(), modifier = Modifier.align(Alignment.TopEnd).background(Color.Red, RoundedCornerShape(50)).padding(horizontal = 3.dp), color = Color.White, fontSize = 8.sp) } },
                            selected = selectedTab == 1, onClick = { selectedTab = 1; onOpenNotifications() })
                        NavigationBarItem(icon = { Icon(Icons.Default.Settings, contentDescription = "الإعدادات", modifier = Modifier.size(24.dp), tint = if (selectedTab == 2) primaryColor else Color.Gray) },
                            label = { Text("الإعدادات", fontSize = 11.sp, color = if (selectedTab == 2) primaryColor else Color.Gray) },
                            selected = selectedTab == 2, onClick = { selectedTab = 2; onOpenSettings() })
                    }
                }
            ) { paddingValues ->
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues).background(backgroundColor)) {
                    // ✅ بانر طقس صغير ومضغوط
                    if (weatherState.weather != null) {
                        CompactWeatherBanner(weather = weatherState.weather, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), isDarkTheme = isDarkTheme)
                    }

                    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.messages) { msg -> 
                            MessageBubble(message = msg, isUser = msg.isFromUser, isDarkTheme = isDarkTheme) 
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(value = state.inputText, onValueChange = viewModel::onInputChange, modifier = Modifier.weight(1f), placeholder = { Text("اكتب رسالتك هنا...", color = Color.Gray) }, shape = RoundedCornerShape(24.dp), maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = Color.Gray, focusedTextColor = if (isDarkTheme) Color.White else Color.Black, unfocusedTextColor = if (isDarkTheme) Color.White else Color.Black))
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { if (state.inputText.isNotBlank()) viewModel.sendMessage() }, modifier = Modifier.background(primaryColor, RoundedCornerShape(50)).size(48.dp)) {
                            Icon(Icons.Default.Send, contentDescription = "إرسال", tint = Color.White)
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun MessageBubble(message: Message, isUser: Boolean, isDarkTheme: Boolean) {
    val bubbleColor = if (isUser) Color(0xFF0EA5E9) else (if (isDarkTheme) Color(0xFF2D2D44) else Color.White)
    val textColor = if (isUser) Color.White else (if (isDarkTheme) Color.White else Color.Black)
    
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(shape = RoundedCornerShape(16.dp), color = bubbleColor, modifier = Modifier.widthIn(max = 250.dp).padding(vertical = 4.dp)) {
            Text(text = message.text, color = textColor, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
fun CompactWeatherBanner(weather: com.racha.ai.domain.model.WeatherInfo, modifier: Modifier = Modifier, isDarkTheme: Boolean) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFF1A1A2E) else Color.White)) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = weather.cityName ?: "موقعك", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color.White else Color.Black)
                Text(text = "${weather.temperature}°C", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color(0xFFBD4CFF) else Color(0xFF0EA5E9))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "💧 ${weather.humidity}%", fontSize = 12.sp, color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Gray)
                Text(text = "💨 ${weather.windSpeed} كم/س", fontSize = 12.sp, color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Gray)
            }
        }
    }
}
