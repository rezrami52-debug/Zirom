package com.racha.ai.ui.screens.chat

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.racha.ai.core.apps.AppChooserDialog
import com.racha.ai.core.tools.PhotoCaptureActivity
import com.racha.ai.core.voice.VoiceInputHelper
import com.racha.ai.data.repository.NotificationRepository
import com.racha.ai.domain.model.Message

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

    var isListening by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    val voiceHelper = remember { VoiceInputHelper(context) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onSpecialToolCompleted(true, "📸 تم حفظ الصورة في مجلد Racha")
        } else {
            viewModel.onSpecialToolCancelled()
        }
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.copyQwenModel(uri)
    }

    LaunchedEffect(state.pendingSpecialTool) {
        if (state.pendingSpecialTool == "open_camera") {
            cameraLauncher.launch(Intent(context, PhotoCaptureActivity::class.java))
        }
    }

    DisposableEffect(Unit) { onDispose { voiceHelper.destroy() } }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    LaunchedEffect(voiceError) {
        if (voiceError != null) { kotlinx.coroutines.delay(3000); voiceError = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.clickable {
                        viewModel.switchLLM(if (state.activeLLM == "qwen") "gemini" else "qwen")
                    }) {
                        Text(text = "Racha AI", fontWeight = FontWeight.Bold)
                        Text(text = if (state.activeLLM == "qwen") "محلي" else "سحابي", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.startNewChat() }) {
                        Icon(Icons.Default.Add, contentDescription = "محادثة جديدة")
                    }
                    IconButton(onClick = onOpenNotifications) {
                        Box {
                            Icon(Icons.Default.Notifications, contentDescription = "الإشعارات")
                            if (unreadCount > 0) {
                                Text(text = unreadCount.toString(), modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.error, RoundedCornerShape(50)).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.onError, fontSize = 10.sp)
                            }
                        }
                    }
                    IconButton(onClick = { showHistoryDialog = true }) {
                        Icon(Icons.Default.Schedule, contentDescription = "المحادثات السابقة")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (weatherState.weather != null) {
                WeatherBanner(weather = weatherState.weather, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages) { msg ->
                    MessageBubble(message = msg)
                }
            }

            if (state.isSearching || state.qwenLoading) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("اكتب رسالتك هنا...") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (state.inputText.isNotBlank()) {
                            // ✅ إرسال الرسالة
                            viewModel.sendMessage()
                        } else if (micPermission.status.isGranted) {
                            // ✅ تسجيل الصوت
                            isListening = true
                            voiceHelper.startListening(
                                onResult = { viewModel.onInputChange(it); isListening = false },
                                onError = { voiceError = it; isListening = false }
                            )
                        } else {
                            micPermission.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)).size(48.dp)
                ) {
                    if (isListening) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    } else if (state.inputText.isNotBlank()) {
                        // ✅ إظهار زر الإرسال عند وجود نص
                        Icon(Icons.Default.Send, contentDescription = "إرسال", tint = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        // ✅ إظهار الميكروفون عند عدم وجود نص
                        Icon(Icons.Default.Mic, contentDescription = "صوت", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            
            if (voiceError != null) {
                Text(text = "️ $voiceError", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 12.sp)
            }
        }
    }

    // نافذة المحادثات السابقة
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("المحادثات السابقة") },
            text = {
                if (state.conversations.isEmpty()) {
                    Text("لا توجد محادثات سابقة", modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(state.conversations) { conv ->
                            val isSelected = conv.id == state.currentConversationId
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                    viewModel.loadConversation(conv.id)
                                    showHistoryDialog = false
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(conv.title, fontWeight = FontWeight.Bold)
                                    if (isSelected) Text("حالي", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showHistoryDialog = false }) { Text("إغلاق") }
            }
        )
    }

    if (state.appChooser.isVisible) {
        AppChooserDialog(
            state = state.appChooser,
            onDismiss = viewModel::onAppChooserDismiss,
            onAppSelected = viewModel::onAppSelected
        )
    }
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
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp).padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (message.isError) {
                    Spacer(modifier = Modifier.padding(top = 4.dp))
                    Text(text = "⚠️ حدث خطأ", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }
    }
}
