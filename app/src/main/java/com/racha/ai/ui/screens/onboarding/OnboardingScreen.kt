package com.racha.ai.ui.screens.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.racha.ai.core.permissions.PermissionHelper
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.ui.components.ModernPermissionCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onOpenApiKeySettings: () -> Unit
) {
    val context = LocalContext.current
    val appPrefs = remember { AppPreferences(context) }

    val notificationPermission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    val locationPermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    )
    val contactPermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.READ_CONTACTS)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعداد Racha", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "مرحباً بك في Racha",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "لتعمل جميع الميزات، نحتاج بعض الصلاحيات:",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 1. الإشعارات
            ModernPermissionCard(
                title = "الوصول للإشعارات",
                description = "لقراءة إشعارات التطبيقات وإخبارك بها صوتياً",
                isGranted = notificationPermission.status.isGranted,
                onActionClick = {
                    if (notificationPermission.status.isGranted) {
                        PermissionHelper.openNotificationListenerSettings(context)
                    } else {
                        notificationPermission.launchPermissionRequest()
                    }
                },
                icon = { Icon(Icons.Filled.Notifications, null, tint = Color(0xFF00E5FF)) }
            )

            // 2. الموقع
            ModernPermissionCard(
                title = "صلاحية الموقع",
                description = "لمواقيت الصلاة وحالة الطقس والخرائط",
                isGranted = locationPermissions.allPermissionsGranted,
                onActionClick = { locationPermissions.launchMultiplePermissionRequest() },
                icon = { Icon(Icons.Filled.Place, null, tint = Color(0xFF00E5FF)) }
            )

            // 3. جهات الاتصال
            val contactLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                // يمكن إضافة منطق هنا إذا لزم الأمر
            }
            ModernPermissionCard(
                title = "جهات الاتصال",
                description = "للبحث عن الأسماء والاتصال بهم عبر Racha",
                isGranted = contactPermissions.allPermissionsGranted,
                onActionClick = {
                    if (!contactPermissions.allPermissionsGranted) {
                        contactLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                    }
                },
                icon = { Icon(Icons.Filled.Person, null, tint = Color(0xFF00E5FF)) }
            )

            // 4. مفتاح API
            ModernPermissionCard(
                title = "مفتاح Gemini API",
                description = "لتفعيل المحادثة الذكية (اختياري — يمكن إضافته لاحقاً)",
                isGranted = appPrefs.isOnboardingComplete(),
                onActionClick = onOpenApiKeySettings,
                icon = { Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF00E5FF)) },
                isOptional = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    appPrefs.setOnboardingComplete(true)
                    onComplete()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text("ابدأ الآن 🚀", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
