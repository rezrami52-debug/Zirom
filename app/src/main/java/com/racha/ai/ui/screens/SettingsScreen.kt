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
import com.racha.ai.data.local.RoutineItem
import com.racha.ai.services.prayer.PrayerAlarmReceiver
import com.racha.ai.services.routine.RoutineAlarmScheduler
import com.racha.ai.services.weather.WeatherWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenFaceEnrollment: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val appPrefs = remember { AppPreferences(context) }
    val faceRepo = remember { FaceAuthRepository(context) }
    val scope = rememberCoroutineScope()

    var currentLanguage by remember { mutableStateOf(appPrefs.getLanguage()) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var faceEnabled by remember { mutableStateOf(faceRepo.isFaceAuthEnabled()) }
    var showDisableDialog by remember { mutableStateOf(false) }

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
            // ===== الملف الشخصي =====
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenProfile() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ملفي الشخصي",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "أخبر Racha عن نفسك",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ===== الحماية بالبصمة =====
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (faceEnabled) {
                            showDisableDialog = true
                        } else {
                            onOpenFaceEnrollment()
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (faceEnabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (faceEnabled) Icons.Filled.Lock else Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "حماية Racha",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (faceEnabled)
                                "✅ مفعّلة — اضغط للتعطيل"
                            else
                                "التقط صورة + سجّل بصمة للتفعيل",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // ===== اللغة =====
            Text(
                text = stringResource(id = R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLanguageDialog = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = LocaleManager.getDisplayName(currentLanguage),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "تغيير",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // ===== مفتاح API =====
            Text(
                text = stringResource(id = R.string.settings_api_key_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("AIza...") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.settings_api_key_hint),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = viewModel::saveApiKey,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.apiKey.isNotBlank()
            ) {
                Text(stringResource(id = R.string.save))
            }

            state.saveMessage?.let { message ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isSaved) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // ===== أدوات الاختبار =====
            Text(
                text = "أدوات الاختبار",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    PrayerAlarmReceiver.triggerManually(context, "الفجر", isPreAlert = true)
                    testMessage = "✅ تم إرسال إشعار اختبار مسبق"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔔 اختبار: إشعار الصلاة المسبق")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    PrayerAlarmReceiver.triggerManually(context, "الفجر", isPreAlert = false)
                    testMessage = "✅ تم إرسال إشعار اختبار الأذان"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🕌 اختبار: إشعار وقت الأذان")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val item = RoutineItem(title = "اختبار", emoji = "⏰", hour = 0, minute = 0)
                    RoutineAlarmScheduler.triggerTest(context, item, isPreAlert = false)
                    testMessage = "✅ تم إرسال إشعار اختبار الروتين"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⏰ اختبار: إشعار روتين")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val ok = WeatherWorker.triggerManually(context)
                        testMessage = if (ok) "✅ تم إرسال إشعار الطقس"
                                     else "⚠️ فشل — تأكد من صلاحية الموقع"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🌤️ اختبار: إشعار الطقس")
            }

            testMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // حوار تعطيل الحماية
    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            title = { Text("تعطيل الحماية؟") },
            text = {
                Text("بعد التعطيل، لن يُطلب منك التحقق عند فتح Racha. هل أنت متأكد؟")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        faceRepo.deleteFaceImage()
                        faceRepo.setFaceAuthEnabled(false)
                        faceEnabled = false
                        showDisableDialog = false
                    }
                ) {
                    Text("تعطيل")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableDialog = false }) {
                    Text("إلغاء")
                }
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
                                    val intent = context.packageManager
                                        .getLaunchIntentForPackage(context.packageName)
                                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    context.startActivity(intent)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (code == currentLanguage) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(id = R.string.back))
                }
            }
        )
    }
}
