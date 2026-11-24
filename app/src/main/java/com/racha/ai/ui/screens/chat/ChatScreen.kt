package com.racha.ai.ui.screens.chat
import com.racha.ai.services.prayer.PrayerTimesCalculator
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.racha.ai.core.eve.drawEveOriginalStyle
import com.racha.ai.core.preferences.AppPreferences

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
import coil.compose.AsyncImage
import java.io.File
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.racha.ai.theme.AppThemeManager
import com.racha.ai.R
import com.racha.ai.core.tools.PhotoCaptureActivity
import com.racha.ai.core.tools.ApkSourceDecompiler.ExtractedFileEntry
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

    val attachmentPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)?.substringBefore(';')?.lowercase().orEmpty()
            val displayName = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                }
            }.getOrNull().orEmpty()
            val extension = displayName.substringAfterLast('.', "").lowercase()
            val supportedImage = mimeType in setOf("image/jpeg", "image/png", "image/webp") ||
                extension in setOf("jpg", "jpeg", "png", "webp")
            if (supportedImage) viewModel.onImageSelected(uri) else viewModel.onFileSelected(uri)
        }
    }

    val decompiledExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) viewModel.exportDecompiledSources(uri)
    }
    var pendingDecompiledFileExportPath by remember { mutableStateOf<String?>(null) }
    val decompiledFileExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val path = pendingDecompiledFileExportPath
        pendingDecompiledFileExportPath = null
        if (uri != null && path != null) viewModel.exportDecompiledFile(path, uri)
    }

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

    var isDrawerOpen by remember { mutableStateOf(false) }

    fun launchEveBubble() {
        val prefs = AppPreferences(context)
        prefs.setEveBubbleEnabled(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        } else {
            ContextCompat.startForegroundService(context, Intent(context, com.racha.ai.core.eve.EveBubbleService::class.java))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
        ) {
            // الهيدر العلوي مع أيقونة إيف التفاعلية وزر القائمة الجانبية
            TopBarRacha(
                glow = primaryGlow,
                unreadCount = unreadCount,
                onAvatarClick = { launchEveBubble() },
                onOpenMenu = { isDrawerOpen = true },
                onNewChat = { viewModel.startNewChat() },
                isLearning = state.isScreenLearningActive
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

                // معاينة الصورة المرفقة
        state.selectedImageUri?.let { uriString ->
            val uri = android.net.Uri.parse(uriString)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, primaryGlow, RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "معاينة الصورة",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "تم إرفاق صورة للتحليل",
                    color = if (isDarkTheme) Color.White else Color.Black,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.clearSelectedImage() }) {
                    Icon(Icons.Default.Close, contentDescription = "إزالة الصورة", tint = Color(0xFFFF5252))
                }
            }
        }

        // معاينة الملف المرفق
        state.selectedFileUri?.let { uriString ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(primaryGlow.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "ملف", tint = primaryGlow, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.selectedFileName ?: "تحليل ملف محلي",
                        color = if (isDarkTheme) Color.White else Color(0xFF0A1B3A),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        state.selectedFileSummary ?: "جارٍ تحليل الملف محليًا...",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 8,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text("لن يُرفع الملف أو يُشغّل؛ النتيجة لا تُحفظ في سجل المحادثة.", color = Color.Gray, fontSize = 10.sp)
                    if (state.selectedFileName.orEmpty().endsWith(".apk", ignoreCase = true)) {
                        when {
                            state.isApkDecompiling -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = primaryGlow
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(onClick = viewModel::cancelApkDecompilation) {
                                        Text("إلغاء", color = primaryGlow)
                                    }
                                }
                            }
                            state.decompiledSourceZipPath != null -> {
                                TextButton(onClick = viewModel::openDecompiledBrowser) {
                                    Text("استعراض الملفات (${state.decompiledFiles.size})", color = primaryGlow)
                                }
                                TextButton(onClick = { decompiledExportLauncher.launch("racha-decompiled-sources.zip") }) {
                                    Text("تصدير جميع النتائج ZIP", color = primaryGlow)
                                }
                            }
                            else -> {
                                TextButton(onClick = viewModel::decompileSelectedApk) {
                                    Text("فكّكي هذا الملف محليًا", color = primaryGlow)
                                }
                                Text("أو اكتبي: فكّكي هذا الملف", color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                        state.apkDecompileStatus?.let { status ->
                            Text(status, color = Color.Gray, fontSize = 10.sp, lineHeight = 14.sp)
                        }
                        if (state.apkDecompileStatus == null) {
                            Text(
                                "تجريبي: لا يشغّل APK، لكن فك ملف غير موثوق يظل يعالج بياناته بمحلل وقد يحمل مخاطر. لا يضمن استعادة المصدر الأصلي.",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
                IconButton(onClick = { viewModel.clearSelectedFile() }) {
                    Icon(Icons.Default.Close, contentDescription = "إزالة الملف", tint = Color(0xFFFF5252))
                }
            }
        }

        // 5. حقل الإدخال
        InputBarRacha(
            text = state.inputText,
            onValueChange = viewModel::onInputChange,
            onSend = {
                if (state.inputText.isNotBlank() || state.pendingImagePath != null || state.selectedFileUri != null) viewModel.sendMessage()
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
            glow = primaryGlow,
            selectedImagePath = state.pendingImagePath,
            hasSelectedFile = state.selectedFileUri != null,
            onAttach = { attachmentPickerLauncher.launch(arrayOf("*/*")) },
            onRemoveAttachment = viewModel::removePendingImage
        )

        }

        // القائمة الجانبية المنزلقة (Side Navigation Drawer)
        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { isDrawerOpen = false }
            )
        }

        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterEnd
            ) {
                SideDrawerRacha(
                    glow = primaryGlow,
                    unreadCount = unreadCount,
                    onClose = { isDrawerOpen = false },
                    onOpenSettings = { isDrawerOpen = false; onOpenSettings() },
                    onOpenNotifications = { isDrawerOpen = false; onOpenNotifications() },
                    onOpenPrayer = { isDrawerOpen = false; onOpenPrayer() },
                    onOpenHistory = { isDrawerOpen = false; onOpenHistory() },
                    onOpenRoutine = { isDrawerOpen = false; onOpenRoutine() },
                    onSecurityScan = {
                        isDrawerOpen = false
                        viewModel.onInputChange("فحص التجسس والأمان")
                        viewModel.sendMessage()
                    }
                )
            }
        }
    }
    if (state.isDecompiledBrowserOpen) {
        DecompiledFilesDialog(
            files = state.decompiledFiles,
            searchQuery = state.decompiledSearchQuery,
            selectedPath = state.selectedDecompiledFilePath,
            preview = state.decompiledFilePreview,
            isLoadingPreview = state.isLoadingDecompiledPreview,
            onSearchQueryChange = viewModel::setDecompiledSearchQuery,
            onSelectFile = viewModel::previewDecompiledFile,
            onDismiss = viewModel::closeDecompiledBrowser,
            onCopyPreview = { text ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("JADX preview", text))
                Toast.makeText(context, "تم نسخ المعاينة محليًا", Toast.LENGTH_SHORT).show()
            },
            onExportFile = { entry ->
                pendingDecompiledFileExportPath = entry.path
                val suggestedName = entry.path.substringAfterLast('/').take(120).ifBlank { "racha-extracted-file" }
                decompiledFileExportLauncher.launch(suggestedName)
            }
        )
    }
}

@Composable
private fun DecompiledFilesDialog(
    files: List<ExtractedFileEntry>,
    searchQuery: String,
    selectedPath: String?,
    preview: String?,
    isLoadingPreview: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectFile: (String) -> Unit,
    onDismiss: () -> Unit,
    onCopyPreview: (String) -> Unit,
    onExportFile: (ExtractedFileEntry) -> Unit
) {
    val visibleFiles = remember(files, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) files else files.filter { it.path.lowercase().contains(query) }
    }
    val selectedFile = files.firstOrNull { it.path == selectedPath }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp, max = 720.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ملفات JADX المستخرجة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${files.size} ملف — المعاينة والتصدير محليان فقط", color = Color.LightGray, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق المتصفح", tint = Color.White)
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("بحث في مسارات الملفات") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp, max = 190.dp),
                    color = Color(0xFF111C30),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
                        Text(selectedPath ?: "اختر ملفًا من القائمة لمعاينته", color = Color(0xFF7DD3FC), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        when {
                            isLoadingPreview -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            preview != null -> SelectionContainer {
                                Text(preview, color = Color.White, fontSize = 11.sp, lineHeight = 16.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                            else -> Text("المعاينة النصية محدودة إلى 128 KB. الملفات الثنائية يمكن تصديرها لفتحها محليًا.", color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        enabled = selectedFile?.isTextPreviewable == true && !preview.isNullOrBlank(),
                        onClick = { preview?.let(onCopyPreview) }
                    ) { Text("نسخ المعاينة") }
                    TextButton(
                        enabled = selectedFile != null,
                        onClick = { selectedFile?.let(onExportFile) }
                    ) { Text("تصدير الملف") }
                }

                Divider(color = Color.White.copy(alpha = 0.16f))
                Text("الملفات الناتجة (${visibleFiles.size})", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(visibleFiles, key = { it.path }) { entry ->
                        val isSelected = entry.path == selectedPath
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF164E63) else Color.Transparent)
                                .clickable { onSelectFile(entry.path) }
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (entry.isTextPreviewable) Icons.Default.Description else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (entry.isTextPreviewable) Color(0xFF7DD3FC) else Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.path, color = Color.White, fontSize = 10.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text("${entry.sizeBytes / 1024} KB", color = Color.LightGray, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopBarRacha(
    glow: Color,
    unreadCount: Int,
    onAvatarClick: () -> Unit,
    onOpenMenu: () -> Unit,
    onNewChat: () -> Unit,
    isLearning: Boolean = false
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(1.5.dp, glow, CircleShape)
                        .clickable { onAvatarClick() }
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(24.dp, 32.dp)) {
                        drawEveOriginalStyle(
                            glow = 1.0f,
                            isSleeping = !isLearning,
                            eyeHeightScale = 1.0f,
                            isNotifying = false,
                            speaking = isLearning
                        )
                    }
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
                Spacer(modifier = Modifier.width(2.dp))
                // زر القائمة الجانبية (ثلاثة خطوط)
                IconButton(onClick = onOpenMenu, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Menu, contentDescription = "القائمة", tint = Color.White)
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF1744))
                            )
                        }
                    }
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
                ContextCompat.startForegroundService(ctx, Intent(ctx, com.racha.ai.core.eve.EveBubbleService::class.java))
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
            ContextCompat.startForegroundService(ctx, intent)
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
    cardBackground: Color,
    cardBorder: Brush,
    glow: Color
) {
    // 1. حساب التحية التلقائية بناءً على ساعة اليوم الحقيقية
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(currentHour) {
        when (currentHour) {
            in 4..11 -> "صباح الخير ☀️"
            in 12..16 -> "طاب يومك 🌤️"
            in 17..22 -> "مساء الخير 🌆"
            else -> "ليلة سعيدة 🌙"
        }
    }

    // 2. حساب مواقيت الصلاة الحقيقية لسوسة بدقة متناهية
    val prayerTimes = remember {
        PrayerTimesCalculator.calculate(
            latitude = 35.8256,
            longitude = 10.6369,
            date = Date(),
            locationName = "سوسة"
        )
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = cardBackground,
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(greeting, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(weather.cityName.ifBlank { "سوسة" }, color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${weather.temperature.toInt()}°C", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("💧 ${weather.humidity}%", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text("💨 ${weather.windSpeed.toInt()} كم/س", color = Color(0xFFE2E8F0), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // مواقيت الصلاة مرتبة بالاتجاه العربي (الفجر في اليمين -> العشاء في اليسار)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PrayerTimeItem("العشاء", timeFormat.format(prayerTimes.isha), glow)
                PrayerTimeItem("المغرب", timeFormat.format(prayerTimes.maghrib), glow)
                PrayerTimeItem("العصر", timeFormat.format(prayerTimes.asr), glow)
                PrayerTimeItem("الظهر", timeFormat.format(prayerTimes.dhuhr), glow)
                PrayerTimeItem("الفجر", timeFormat.format(prayerTimes.fajr), glow)
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
                message.imagePath?.let { imagePath ->
                    AsyncImage(
                        model = File(imagePath),
                        contentDescription = "الصورة المرفقة",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    if (message.text.isNotBlank()) Spacer(modifier = Modifier.height(8.dp))
                }

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
    glow: Color,
    selectedImagePath: String? = null,
    hasSelectedFile: Boolean = false,
    onAttach: () -> Unit = {},
    onRemoveAttachment: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        selectedImagePath?.let { imagePath ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.92f))
                    .border(1.dp, glow.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = File(imagePath),
                    contentDescription = "معاينة الصورة المختارة",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ستُرسل الصورة إلى Gemini للتحليل عند الضغط على إرسال.",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemoveAttachment, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إزالة الصورة", tint = Color.LightGray)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAttach, enabled = !isListening) {
                Icon(Icons.Default.AttachFile, contentDescription = "إرفاق صورة أو ملف", tint = glow)
            }

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
                            imageVector = if (text.isNotBlank() || selectedImagePath != null || hasSelectedFile) Icons.Default.Send else Icons.Default.Mic,
                            contentDescription = "إرسال الرسالة أو الصورة",
                            tint = glow,
                            modifier = Modifier.size(22.dp)
                        )
                    }
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


@Composable
fun SideDrawerRacha(
    glow: Color,
    unreadCount: Int,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPrayer: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRoutine: () -> Unit,
    onSecurityScan: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(290.dp),
        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
        color = Color(0xFF0A0F1D).copy(alpha = 0.98f),
        border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(glow.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f)))),
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // هيدر القائمة مع زر الإغلاق
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A))
                            .border(1.5.dp, glow, CircleShape)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(20.dp, 28.dp)) {
                            drawEveOriginalStyle(1.0f, false, 1.0f, false, false)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Racha AI", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("القائمة التكتيكية", color = glow, fontSize = 10.sp)
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.Gray, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Divider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // عناصر القائمة
            DrawerMenuRow(
                icon = Icons.Default.Shield,
                title = "فحص التجسس والأمان",
                glow = Color(0xFF00E5FF),
                onClick = onSecurityScan
            )
            Spacer(modifier = Modifier.height(10.dp))

            DrawerMenuRow(icon = Icons.Default.Settings, title = "الإعدادات", glow = glow, onClick = onOpenSettings)
            Spacer(modifier = Modifier.height(10.dp))

            DrawerMenuRow(icon = Icons.Default.Notifications, title = "الإشعارات", badge = unreadCount, glow = glow, onClick = onOpenNotifications)
            Spacer(modifier = Modifier.height(10.dp))

            DrawerMenuRow(icon = Icons.Default.Mosque, title = "القبلة ومواقيت الصلاة", glow = glow, onClick = onOpenPrayer)
            Spacer(modifier = Modifier.height(10.dp))

            DrawerMenuRow(icon = Icons.Default.History, title = "سجل المحادثات", glow = glow, onClick = onOpenHistory)
            Spacer(modifier = Modifier.height(10.dp))

            DrawerMenuRow(icon = Icons.Default.Schedule, title = "الروتين اليومي", glow = glow, onClick = onOpenRoutine)
        }
    }
}

@Composable
fun DrawerMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    badge: Int = 0,
    glow: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E293B).copy(alpha = 0.45f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = title, tint = glow, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(if (badge > 99) "99+" else badge.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
