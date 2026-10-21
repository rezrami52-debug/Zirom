package com.racha.ai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.services.prayer.PrayerAlarmReceiver
import com.racha.ai.services.routine.RoutineAlarmScheduler
import com.racha.ai.services.weather.WeatherWorker
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
    val scope = rememberCoroutineScope()

    var currentLanguage by remember { mutableStateOf(appPrefs.getLanguage()) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf(state.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ✅ زر التخصيص
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenCustomization() },
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
                modifier = Modifier.fillMaxWidth().clickable { onOpenProfile() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("ملفي الشخصي", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("أخبر Racha عن نفسك", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }

            // ===== الأمان =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("الأمان", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // حماية Racha
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenFaceEnrollment() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("حماية Racha", fontWeight = FontWeight.Bold)
                            Text("التقاط صورة + تسجيل بصمة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }

                    Divider()

                    // التقاط صورة المتسلل
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("التقاط صورة المتسلل", fontWeight = FontWeight.Bold)
                            Text("صورة بعد عدد محاولات فاشلة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = appPrefs.isIntruderPhotoEnabled(),
                            onCheckedChange = { appPrefs.setIntruderPhotoEnabled(it) }
                        )
                    }

                    Divider()

                    // عدد المحاولات
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("عدد المحاولات قبل التصوير", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { appPrefs.setIntruderThreshold(appPrefs.getIntruderThreshold() - 1) }) {
                                Icon(Icons.Default.Remove, contentDescription = null)
                            }
                            Text("${appPrefs.getIntruderThreshold()}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { appPrefs.setIntruderThreshold(appPrefs.getIntruderThreshold() + 1) }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                        }
                    }

                    Divider()

                    // سجل المحاولات
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenSecurityLog() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("عرض سجل المحاولات", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ===== حارس النوم =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("حارس النوم", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تذكير لطيف بالنوم", fontWeight = FontWeight.Bold)
                            Text("إذا كنت مستيقظاً متأخراً", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = appPrefs.isSleepGuardianEnabled(),
                            onCheckedChange = { appPrefs.setSleepGuardianEnabled(it) }
                        )
                    }
                }
            }

            // ===== اللغة =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("اللغة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showLanguageDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (currentLanguage == "ar") "العربية" else if (currentLanguage == "fr") "Français" else "English")
                    }
                }
            }

            // ===== مفتاح API =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("مفتاح Gemini API", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("أدخل المفتاح") },
                        visualTransformation = if (showApiKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.saveApiKey(apiKeyInput) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = apiKeyInput.isNotBlank()
                    ) {
                        Text("حفظ المفتاح")
                    }
                    if (state.apiKeySaved) {
                        Text("✅ تم حفظ المفتاح بنجاح", color = Color.Green, fontSize = 12.sp)
                    }
                }
            }

            // ===== أدوات الاختبار =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("أدوات الاختبار", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            PrayerAlarmReceiver.scheduleTestNotification(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔔 اختبار إشعار الصلاة")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            PrayerAlarmReceiver.scheduleTestAdhan(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🕌 اختبار إشعار الأذان")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            RoutineAlarmScheduler.scheduleTestNotification(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("⏰ اختبار إشعار روتين")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            WeatherWorker.scheduleTest(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🌤️ اختبار إشعار الطقس")
                    }
                }
            }
        }
    }

    // حوار اختيار اللغة
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("اختر اللغة") },
            text = {
                Column {
                    listOf("ar" to "العربية", "fr" to "Français", "en" to "English").forEach { (code, name) ->
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
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
