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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.racha.ai.R
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

    // ✅ SAF Picker للنموذج
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.copyQwenModel(uri)
        }
    }

    LaunchedEffect(state.pendingSpecialTool) {
        when (state.pendingSpecialTool) {
            "open_camera" -> {
                val intent = Intent(context, PhotoCaptureActivity::class.java)
                cameraLauncher.launch(intent)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { voiceHelper.destroy() }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    LaunchedEffect(voiceError) {
        if (voiceError != null) {
            kotlinx.coroutines.delay(3000)
            voiceError = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable {
                            val newLLM = if (state.activeLLM == "qwen") "gemini" else "qwen"
                            viewModel.switchLLM(newLLM)
                        }
                    ) {
                        Text(
                            stringResource(id = R.string.app_name),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (state.activeLLM == "qwen")
                                "🟢 Qwen محلي — اضغط للتبديل"
                            else
                                "🔵 Gemini سحابي — اضغط للتبديل",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenRoutine) {
                        Icon(Icons.Filled.Checklist, "روتيني")
                    }
                    IconButton(onClick = onOpenPrayer) {
                        Icon(Icons.Filled.Schedule, stringResource(id = R.string.prayer_title))
                    }
                    IconButton(onClick = onOpenNotifications) {
                        Box {
                            Icon(Icons.Filled.Notifications, stringResource(id = R.string.notifications_title))
                            if (unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(10.dp)
                                        .background(Color(0xFF4CAF50), CircleShape)
                                )
                            }
                        }
                    }
                    IconButton(onClick = viewModel::clearHistory) {
                        Icon(Icons.Filled.Delete, "مسح")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, stringResource(id = R.string.open_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            WeatherBanner(weather = weatherState.weather)

            // ✅ بطاقة "اختر نموذج Qwen" عند الحاجة
            if (state.activeLLM == "qwen" && !state.qwenModelReady) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    ) {
                        Text(
                            text = "📦 نموذج Qwen غير موجود",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "اختر ملف qwen2.5-1.5b-instruct.task (1.5 GB) من الملفات",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                // فتح SAF Picker لاختيار ملف task
                                modelPickerLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📁 اختر ملف النموذج")
                        }
                    }
                }
            }

            if (state.qwenLoading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "⏳ Qwen يحمّل النموذج... (30-60 ثانية أول مرة)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isListening) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(id = R.string.voice_listening),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            voiceError?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "⚠️ $error",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.size(8.dp)) }
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
                if (state.isSending) {
                    item { TypingIndicator() }
                }
                item { Spacer(modifier = Modifier.size(8.dp)) }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = viewModel::onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(id = R.string.chat_input_hint)) },
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            if (isListening) {
                                voiceHelper.stopListening()
                                isListening = false
                                return@IconButton
                            }
                            if (!micPermission.status.isGranted) {
                                micPermission.launchPermissionRequest()
                                return@IconButton
                            }
                            isListening = true
                            voiceHelper.startListening(
                                languageTag = "ar-TN",
                                onReady = {},
                                onResult = { text ->
                                    isListening = false
                                    viewModel.onInputChange(text)
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        viewModel.sendMessage()
                                    }, 300)
                                },
                                onError = { error ->
                                    isListening = false
                                    voiceError = error
                                },
                                onEnd = {}
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "صوت",
                            tint = if (isListening) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = viewModel::sendMessage,
                        enabled = state.inputText.isNotBlank() && !state.isSending
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = stringResource(id = R.string.chat_send),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val bubbleColor = when {
        message.isFromUser -> MaterialTheme.colorScheme.primary
        message.isError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        message.isFromUser -> MaterialTheme.colorScheme.onPrimary
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        contentAlignment = alignment
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(16.dp),
            color = bubbleColor
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                color = textColor,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.chat_thinking),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
