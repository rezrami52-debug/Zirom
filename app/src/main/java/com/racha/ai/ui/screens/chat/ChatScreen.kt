package com.racha.ai.ui.screens.chat

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.racha.ai.R
import com.racha.ai.core.apps.AppChooserDialog
import com.racha.ai.core.tools.PhotoCaptureActivity
import com.racha.ai.core.voice.VoiceInputHelper
import com.racha.ai.data.repository.NotificationRepository
import com.racha.ai.domain.model.Message
import com.racha.ai.ui.theme.DayMessageAI
import com.racha.ai.ui.theme.DayMessageUser
import com.racha.ai.ui.theme.NightMessageAI
import com.racha.ai.ui.theme.NightMessageUser
import com.racha.ai.ui.theme.PrayerGold
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
    
    val isDarkTheme = MaterialTheme.colorScheme.background == Color(0xFF0A0A0F)

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.onSpecialToolCompleted(true, "📸 تم حفظ الصورة")
        else viewModel.onSpecialToolCancelled()
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) viewModel.copyQwenModel(uri) }

    LaunchedEffect(state.pendingSpecialTool) {
        if (state.pendingSpecialTool == "open_camera") cameraLauncher.launch(Intent(context, PhotoCaptureActivity::class.java))
    }
    DisposableEffect(Unit) { onDispose { voiceHelper.destroy() } }
    LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1) }
    LaunchedEffect(voiceError) { if (voiceError != null) { kotlinx.coroutines.delay(3000); voiceError = null } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Surface(modifier = Modifier.fillMaxHeight().width(300.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 16.dp) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("المحادثات السابقة", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (isDarkTheme) Color(0xFFBD4CFF) else Color(0xFF0EA5E9))
                        IconButton(onClick = { scope.launch { drawerState.close() } }) { 
                            Icon(Icons.Default.Close, contentDescription = "إغلاق") 
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.conversations.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                            Text("لا توجد محادثات سابقة") 
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.conversations) { conv ->
                                val isSelected = conv.id == state.currentConversationId
                                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar")).format(Date(conv.timestamp))
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { viewModel.loadConversation(conv.id); scope.launch { drawerState.close() } },
                                    colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(text = conv.title, fontWeight = FontWeight.Bold, maxLines = 2)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = dateStr, fontSize = 12.sp)
                                        if (isSelected) { 
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = "✓ حالي", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) 
                                        }
                                    }
                                }
                            }
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.startNewChat(); scope.launch { drawerState.close() } }, 
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("محادثة جديدة")
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // ✅ صورة EVE الدائرية
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isDarkTheme) Color(0xFF1A1A2E) else Color(0xFFE8F4F8))
                                        .border(2.dp, if (isDarkTheme) Color(0xFFBD4CFF) else Color(0xFF0EA5E9), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🤖",
                                        fontSize = 24.sp
                                    )
                                }
                                Text(text = "Racha AI", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "القائمة")
                            }
                        },
                        actions = { },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                bottomBar = {
                    BottomAppBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        tonalElevation = 8.dp
                    ) {
                        // ✅ 1. القبلة
                        NavigationBarItem(
                            icon = { 
                                Icon(
                                    Icons.Default.Mosque, 
                                    contentDescription = "القبلة", 
                                    modifier = Modifier.size(24.dp),
                                    tint = if (selectedTab == 0) (if (isDarkTheme) Color(0xFFBD4CFF) else PrayerGold) else Color.Gray
                                ) 
                            },
                            label = { Text("القبلة", fontSize = 11.sp) },
                            selected = selectedTab == 0,
                            onClick = { 
                                selectedTab = 0
                                onOpenPrayer()
                            }
                        )
                        // ✅ 2. الإشعارات
                        NavigationBarItem(
                            icon = { 
                                Icon(
                                    Icons.Default.Notifications, 
                                    contentDescription = "الإشعارات", 
                                    modifier = Modifier.size(24.dp),
                                    tint = if (selectedTab == 1) (if (isDarkTheme) Color(0xFFBD4CFF) else Color(0xFF0EA5E9)) else Color.Gray
                                ) 
                            },
                            label = { 
                                Box {
                                    Text("الإشعارات", fontSize = 11.sp)
                                    if (unreadCount > 0) {
                                        Text(
                                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                            modifier = Modifier.align(Alignment.TopEnd).background(Color.Red, RoundedCornerShape(50)).padding(horizontal = 3.dp),
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            selected = selectedTab == 1,
                            onClick = { 
                                selectedTab = 1
                                onOpenNotifications()
                            }
                        )
                        // ✅ 3. الإعدادات
                        NavigationBarItem(
                            icon = { 
                                Icon(
                                    Icons.Default.Settings, 
                                    contentDescription = "الإعدادات", 
                                    modifier = Modifier.size(24.dp),
                                    tint = if (selectedTab == 2) (if (isDarkTheme) Color(0xFFBD4CFF) else Color(0xFF0EA5E9)) else Color.Gray
                                ) 
                            },
                            label = { Text("الإعدادات", fontSize = 11.sp) },
                            selected = selectedTab == 2,
                            onClick = { 
                                selectedTab = 2
                                onOpenSettings()
                            }
                        )
                    }
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (isDarkTheme) 
                                    listOf(Color(0xFF0A0A0F), Color(0xFF1A1A2E))
                                else 
                                    listOf(Color(0xFFE8F4F8), Color(0xFF4A90E2))
                            )
                        )
                ) {
                    when {
                        weatherState.isLoading -> {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري تحديث الطقس...")
                            }
                        }
                        weatherState.error != null -> {
                            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOff, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(weatherState.error ?: "خطأ في الطقس")
                                }
                            }
                        }
                        weatherState.weather != null -> {
                            WeatherBannerWithPrayerTimes(weather = weatherState.weather, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }

                    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.messages) { msg -> MessageBubble(message = msg, isDarkTheme = isDarkTheme) }
                    }

                    if (state.isSearching || state.qwenLoading) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) { 
                            CircularProgressIndicator() 
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = state.inputText, onValueChange = viewModel::onInputChange, modifier = Modifier.weight(1f),
                            placeholder = { Text("اكتب رسالتك هنا...") }, 
                            shape = RoundedCornerShape(24.dp), 
                            maxLines = 4,
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isDarkTheme) Color(0xFFBD4CFF) else Color(0xFF0EA5E9),
                                unfocusedBorderColor = Color.Gray,
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (state.inputText.isNotBlank()) viewModel.sendMessage()
                                else if (micPermission.status.isGranted) {
                                    isListening = true
                                    voiceHelper.startListening(onResult = { viewModel.onInputChange(it); isListening = false }, onError = { voiceError = it; isListening = false })
                                } else { micPermission.launchPermissionRequest() }
                            },
                            modifier = Modifier
                                .background(if (isDarkTheme) Color(0xFFBD4CFF) else Color(0xFF0EA5E9), RoundedCornerShape(50))
                                .size(48.dp)
                        ) {
                            if (isListening) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            else if (state.inputText.isNotBlank()) Icon(Icons.Default.Send, contentDescription = "إرسال", tint = Color.White)
                            else Icon(Icons.Default.Mic, contentDescription = "صوت", tint = Color.White)
                        }
                    }
                    if (voiceError != null) { Text(text = "⚠️ $voiceError", color = Color.Red, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 12.sp) }
                }
            }
        }
    )
}

@Composable
fun MessageBubble(message: Message, modifier: Modifier = Modifier, isDarkTheme: Boolean) {
    val isUser = message.isFromUser
    val userColor = if (isDarkTheme) DayMessageUser else DayMessageUser
    val aiColor = if (isDarkTheme) NightMessageAI else DayMessageAI
    
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isUser) 16.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 16.dp),
            color = if (isUser) userColor else aiColor,
            modifier = Modifier.widthIn(max = 280.dp).padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = message.text, color = Color.White)
                if (message.isError) { 
                    Spacer(modifier = Modifier.padding(top = 4.dp))
                    Text(text = "⚠️ حدث خطأ", color = Color(0xFFE94560), fontSize = 12.sp) 
                }
            }
        }
    }
}
