package com.racha.ai.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.racha.ai.R
import com.racha.ai.core.faceauth.FaceAuthRepository
import com.racha.ai.core.locale.LocaleManager
import com.racha.ai.core.preferences.AppPreferences
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableIntStateOf
import com.racha.ai.data.local.RoutineItem
import com.racha.ai.services.prayer.PrayerAlarmReceiver
import com.racha.ai.services.routine.RoutineAlarmScheduler
import com.racha.ai.services.weather.WeatherWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenCustomization: () -> Unit = {},
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenFaceEnrollment: () -> Unit,
    onOpenSecurityLog: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val appPrefs = remember { AppPreferences(context) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val faceRepo = remember { FaceAuthRepository(context) }
    val scope = rememberCoroutineScope()

    var currentLanguage by remember { mutableStateOf(appPrefs.getLanguage()) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var faceEnabled by remember { mutableStateOf(faceRepo.isFaceAuthEnabled()) }
    var showDisableDialog by remember { mutableStateOf(false) }

    var intruderEnabled by remember { mutableStateOf(appPrefs.isIntruderPhotoEnabled()) }
    var intruderThreshold by remember { mutableStateOf(appPrefs.getIntruderThreshold()) }

    // SleepGuardian
    var sleepGuardianEnabled by remember { mutableStateOf(appPrefs.isSleepGuardianEnabled()) }
    var sleepStartHour by remember { mutableStateOf(appPrefs.getSleepGuardianStartHour()) }

    LaunchedEffect(state.saveMessage) {
        if (state.saveMessage != null) {
            delay(3000)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(testMessage) {
        if (testMessage != null) {
            delay(3000)
            testMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.settings_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ✅ زر التخصيص
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onOpenCustomization() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎨", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("التخصيص", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("الألوان، الشفافية، التوهج", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }

            // ===== الملف الشخصي =====
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenProfile() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ملفي الشخصي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("أخبر Racha عن نفسك", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ===== حماية Racha =====
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (faceEnabled) showDisableDialog = true else onOpenFaceEnrollment()
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (faceEnabled) MaterialTheme.colorScheme.primaryContainer
                                     else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (faceEnabled) Icons.Filled.Lock else Icons.Filled.PhotoCamera, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("حماية Racha", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (faceEnabled) "✅ مفعّلة — اضغط للتعطيل" else "التقط صورة + سجّل بصمة",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // ===== الأمان =====
            Text("الأمان", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // التقاط صورة المتسلل
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Security, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("التقاط صورة المتسلل", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("صورة بعد عدد محاولات فاشلة", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = intruderEnabled,
                        onCheckedChange = {
                            intruderEnabled = it
                            appPrefs.setIntruderPhotoEnabled(it)
                        }
                    )
                }
            }

            if (intruderEnabled) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("عدد المحاولات قبل التصوير", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    if (intruderThreshold > 1) {
                                        intruderThreshold--
                                        appPrefs.setIntruderThreshold(intruderThreshold)
                                    }
                                },
                                enabled = intruderThreshold > 1
                            ) { Text("−") }
                            Spacer(Modifier.width(16.dp))
                            Text("$intruderThreshold", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(16.dp))
                            Button(
                                onClick = {
                                    if (intruderThreshold < 10) {
                                        intruderThreshold++
                                        appPrefs.setIntruderThreshold(intruderThreshold)
                                    }
                                },
                                enabled = intruderThreshold < 10
                            ) { Text("+") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenSecurityLog,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Security, null)
                Spacer(Modifier.width(8.dp))
                Text("📋 عرض سجل المحاولات")
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // ===== حارس النوم =====
            Text("حارس النوم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🌙", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("تذكير لطيف بالنوم", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("إذا كنت مستيقظاً متأخراً", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = sleepGuardianEnabled,
                        onCheckedChange = {
                            sleepGuardianEnabled = it
                            appPrefs.setSleepGuardianEnabled(it)
                        }
                    )
                }
            }

            if (sleepGuardianEnabled) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("وقت البدء", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    if (sleepStartHour > 20) {
                                        sleepStartHour--
                                        appPrefs.setSleepGuardianStartHour(sleepStartHour)
                                    }
                                },
                                enabled = sleepStartHour > 20
                            ) { Text("−") }
                            Spacer(Modifier.width(16.dp))
                            Text("$sleepStartHour:00", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(16.dp))
                            Button(
                                onClick = {
                                    if (sleepStartHour < 23) {
                                        sleepStartHour++
                                        appPrefs.setSleepGuardianStartHour(sleepStartHour)
                                    }
                                },
                                enabled = sleepStartHour < 23
                            ) { Text("+") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // ===== اللغة =====
            Text(stringResource(id = R.string.settings_language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLanguageDialog = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(LocaleManager.getDisplayName(currentLanguage), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("تغيير", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // ===== مفتاح API =====
            Text(stringResource(id = R.string.settings_api_key_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("AIza...") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(id = R.string.settings_api_key_hint), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::saveApiKey,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.apiKey.isNotBlank()
            ) { Text(stringResource(id = R.string.save)) }

            state.saveMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // ===== أدوات الاختبار =====
            Text("أدوات الاختبار", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    PrayerAlarmReceiver.triggerManually(context, "الفجر", isPreAlert = true)
                    testMessage = "✅ تم إرسال إشعار اختبار مسبق"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("🔔 اختبار: إشعار الصلاة المسبق") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    PrayerAlarmReceiver.triggerManually(context, "الفجر", isPreAlert = false)
                    testMessage = "✅ تم إرسال إشعار اختبار الأذان"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("🕌 اختبار: إشعار وقت الأذان") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val item = RoutineItem(title = "شرب الماء", emoji = "💧", hour = 0, minute = 0)
                    RoutineAlarmScheduler.triggerTest(context, item, isPreAlert = false)
                    testMessage = "✅ تم إرسال إشعار اختبار الروتين"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("⏰ اختبار: إشعار روتين") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val ok = WeatherWorker.triggerManually(context)
                        testMessage = if (ok) "✅ تم إرسال إشعار الطقس" else "⚠️ فشل — تأكد من صلاحية الموقع"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("🌤️ اختبار: إشعار الطقس") }

            testMessage?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // حوار تعطيل الحماية
    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            title = { Text("تعطيل الحماية؟") },
            text = { Text("بعد التعطيل، لن يُطلب منك التحقق عند فتح Racha.") },
            confirmButton = {
                TextButton(onClick = {
                    faceRepo.deleteFaceImage()
                    faceRepo.setFaceAuthEnabled(false)
                    faceEnabled = false
                    showDisableDialog = false
                }) { Text("تعطيل") }
            },
            dismissButton = {
                TextButton(onClick = { showDisableDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // حوار اختيار اللغة
    if (showLanguageDialog) {
        val languages = LocaleManager.getSupportedLanguages()
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(id = R.string.settings_language)) },
            text = {
                Column {
                    for (pair in languages) {
                        val code = pair.first
                        val name = pair.second
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    appPrefs.setLanguage(code)
                                    currentLanguage = code
                                    showLanguageDialog = false
                                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    context.startActivity(intent)
                    )
                    
                    // شدة التوهج
                    Text("شدة التوهج: ${String.format("%.1f", appPrefs.getGlowIntensity())}x", fontSize = 14.sp)
                    Slider(
                        value = appPrefs.getGlowIntensity(),
                        onValueChange = { appPrefs.setGlowIntensity(it); refreshKey++ },
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }

}
