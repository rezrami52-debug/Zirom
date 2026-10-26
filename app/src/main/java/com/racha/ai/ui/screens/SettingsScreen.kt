package com.racha.ai.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.racha.ai.core.eve.EveAccessibilityService
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.core.shizuku.ShizukuShellRunner
import com.racha.ai.data.llm.QwenService
import com.racha.ai.theme.AppThemeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeManager: AppThemeManager,
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
    val qwenService = remember { QwenService(context) }
    val themeState by themeManager.themeState.collectAsState()
    val cardBgColor = themeState.rachaBubbleColor.copy(alpha = themeState.elementsAlpha)
    val headerCardBgColor = themeState.glowColor.copy(alpha = themeState.elementsAlpha)

    var currentLanguage by remember { mutableStateOf(appPrefs.getLanguage()) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var selectedLLMProvider by remember { mutableStateOf(appPrefs.getLLMProvider()) }

    var isAccessibilityActive by remember { mutableStateOf(EveAccessibilityService.isRunning()) }
    var isMediaPermissionGranted by remember { mutableStateOf(checkMediaPermission(context)) }
    var isShizukuActive by remember { mutableStateOf(checkShizukuStatus()) }
    var isQwenInstalled by remember { mutableStateOf(qwenService.isModelPresent()) }

    var ttsSpeed by remember { mutableFloatStateOf(appPrefs.getTtsSpeed()) }
    var ttsPitch by remember { mutableFloatStateOf(appPrefs.getTtsPitch()) }
    var mosqueDuration by remember { mutableIntStateOf(appPrefs.getMosqueDuration()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("لوحة التحكم والإعدادات", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 🎨 التخصيص
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenCustomization() },
                colors = CardDefaults.cardColors(containerColor = headerCardBgColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎨", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("التخصيص والمظهر", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("ألوان الفقاعات، الشفافية، التوهج", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }

            // 👤 الملف الشخصي
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenProfile() },
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("ملفي الشخصي", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("أخبر Racha عن نفسك لتخصيص الحوار", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }

            // 🤖 محرك الذكاء الاصطناعي واختيار المزوّد
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("محرك الذكاء الاصطناعي 🤖", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // اختيار المزوّد
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedLLMProvider != "qwen",
                            onClick = {
                                selectedLLMProvider = "cloud"
                                appPrefs.setLLMProvider("cloud")
                            },
                            label = { Text("سحابي (Groq ⚡)") },
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = selectedLLMProvider == "qwen",
                            onClick = {
                                selectedLLMProvider = "qwen"
                                appPrefs.setLLMProvider("qwen")
                            },
                            label = { Text("محلي (Qwen أوفلاين 📱)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (selectedLLMProvider == "qwen") {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isQwenInstalled) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (isQwenInstalled) "✅" else "⚠️", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isQwenInstalled) "تم العثور على Qwen 2.5 (1.5GB) في /Racha/models ومستعد للعمل بدون إنترنت!" else "ملف Qwen غير موجود في مجلد Racha/models",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isQwenInstalled) Color(0xFF00E676) else Color(0xFFFF5252)
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = state.apiKey,
                            onValueChange = { viewModel.onApiKeyChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("أدخل مفتاح الـ API (Groq أو Gemini)") },
                            visualTransformation = if (showApiKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.testApiKeyConnection() },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isTestingKey && state.apiKey.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                if (state.isTestingKey) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("فحص الاتصال ⚡", fontSize = 13.sp)
                                }
                            }

                            Button(
                                onClick = { viewModel.saveApiKey() },
                                modifier = Modifier.weight(1f),
                                enabled = state.apiKey.isNotBlank()
                            ) {
                                Text("حفظ المفتاح 💾", fontSize = 13.sp)
                            }
                        }

                        if (state.testMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.testMessage!!,
                                color = if (state.isTestSuccess == true) Color(0xFF00E676) else Color(0xFFFF5252),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 🛠️ مركز الصلاحيات وحالة النظام
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("مركز الصلاحيات وحالة النظام 🛠️", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("خدمة Shizuku (التحكم العميق)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(if (isShizukuActive) "متصل وجاهز للعمل ✅" else "غير متصل ⚠️", fontSize = 11.sp, color = if (isShizukuActive) Color(0xFF00E676) else Color.Gray)
                        }
                        Button(
                            onClick = {
                                val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                if (intent != null) context.startActivity(intent)
                                else openUrl(context, "https://shizuku.rikka.app")
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("فتح Shizuku", fontSize = 11.sp)
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("إمكانية الوصول (Accessibility)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(if (isAccessibilityActive) "مفعلة وتعمل ✅" else "معطلة ⚠️", fontSize = 11.sp, color = if (isAccessibilityActive) Color(0xFF00E676) else Color.Gray)
                        }
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                context.startActivity(intent)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("تفعيل الخدمة", fontSize = 11.sp)
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("الوصول لكافة ملفات الوسائط", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(if (isMediaPermissionGranted) "الصلاحية ممنوحة ✅" else "مطلوب لتشغيل الصور والفيديوهات ⚠️", fontSize = 11.sp, color = if (isMediaPermissionGranted) Color(0xFF00E676) else Color.Gray)
                        }
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } else {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("منح الإذن", fontSize = 11.sp)
                        }
                    }
                }
            }

            // 🗣️ صوت EVE والحوار المستمر
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("صوت EVE وتجربة الحوار 🗣️", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الحوار المستمر التلقائي", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("فتح المايك تلقائياً بعد كل رد لاستمرار الحديث دون لمس", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = appPrefs.isContinuousChatEnabled(),
                            onCheckedChange = { appPrefs.setContinuousChatEnabled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("سرعة الكلام: ${String.format("%.2f", ttsSpeed)}x", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = ttsSpeed,
                        onValueChange = {
                            ttsSpeed = it
                            appPrefs.setTtsSpeed(it)
                        },
                        valueRange = 0.7f..1.3f
                    )

                    Text("نبرة الصوت: ${String.format("%.2f", ttsPitch)}x", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = ttsPitch,
                        onValueChange = {
                            ttsPitch = it
                            appPrefs.setTtsPitch(it)
                        },
                        valueRange = 0.8f..1.3f
                    )
                }
            }

            // 🫧 الفقاعة العائمة
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("الفقاعة العائمة (EVE Bubble) 🫧", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الالتصاق التلقائي بالحافة", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("قفز الفقاعة بنعومة لحافة الشاشة بعد سحبها", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = appPrefs.isEdgeSnapEnabled(),
                            onCheckedChange = { appPrefs.setEdgeSnapEnabled(it) }
                        )
                    }
                }
            }

            // 🕌 وضع المسجد ومواقيت الصلاة
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("وضع المسجد ومواقيت الصلاة 🕌", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("وضع المسجد التلقائي", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("تحويل الهاتف لوضع الصامت تلقائياً عند وقت الصلاة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = appPrefs.isMosqueModeEnabled(),
                            onCheckedChange = { appPrefs.setMosqueModeEnabled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("مدة الصامت في المسجد", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                if (mosqueDuration > 15) {
                                    mosqueDuration -= 5
                                    appPrefs.setMosqueDuration(mosqueDuration)
                                }
                            }) { Icon(Icons.Default.Remove, contentDescription = null) }
                            Text("$mosqueDuration دقيقة", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = {
                                if (mosqueDuration < 45) {
                                    mosqueDuration += 5
                                    appPrefs.setMosqueDuration(mosqueDuration)
                                }
                            }) { Icon(Icons.Default.Add, contentDescription = null) }
                        }
                    }
                }
            }

            // 📢 أين هاتفي
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ميزة أين هاتفي (Find My Phone) 📢", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("وميض الفلاش (Strobe) عند النداء", fontSize = 13.sp)
                        Switch(
                            checked = appPrefs.isFindPhoneStrobeEnabled(),
                            onCheckedChange = { appPrefs.setFindPhoneStrobeEnabled(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الرنين بأعلى صوت حتى في الصامت", fontSize = 13.sp)
                        Switch(
                            checked = appPrefs.isFindPhoneMaxVolumeEnabled(),
                            onCheckedChange = { appPrefs.setFindPhoneMaxVolumeEnabled(it) }
                        )
                    }
                }
            }

            // 🔒 الأمان ومصيدة المتسلل
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("الأمان 🔒", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

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

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("التقاط صورة المتسلل", fontWeight = FontWeight.Bold)
                            Text("صورة سريّة بعد عدد محاولات فاشلة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = appPrefs.isIntruderPhotoEnabled(),
                            onCheckedChange = { appPrefs.setIntruderPhotoEnabled(it) }
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

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

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

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

            // 🌙 حارس النوم
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("حارس النوم 🌙", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تذكير لطيف بالنوم", fontWeight = FontWeight.Bold)
                            Text("تنبيه هادئ إذا كنت مستيقظاً في وقت متأخر", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = appPrefs.isSleepGuardianEnabled(),
                            onCheckedChange = { appPrefs.setSleepGuardianEnabled(it) }
                        )
                    }
                }
            }

            // 🌐 اللغة
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("اللغة 🌐", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showLanguageDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (currentLanguage == "ar") "العربية" else if (currentLanguage == "fr") "Français" else "English")
                    }
                }
            }
        }
    }

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
                TextButton(onClick = { showLanguageDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

private fun checkShizukuStatus(): Boolean {
    return try {
        val res = ShizukuShellRunner.execCommand("echo ok")
        res.contains("ok")
    } catch (_: Exception) { false }
}

private fun checkMediaPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    } catch (_: Exception) {}
}
