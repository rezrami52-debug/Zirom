package com.racha.ai.ui.screens.onboarding

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.racha.ai.core.permissions.PermissionHelper
import com.racha.ai.core.preferences.AppPreferences

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onOpenApiKeySettings: () -> Unit
) {
    val context = LocalContext.current
    val appPrefs = remember { AppPreferences(context) }

    // حالة صلاحية الإشعارات
    var hasNotificationPermission by remember {
        mutableStateOf(PermissionHelper.isNotificationListenerEnabled(context))
    }

    // حالة صلاحية الموقع
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // تحديث الحالة كل ثانيتين (لأن المستخدم قد يعود)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            hasNotificationPermission =
                PermissionHelper.isNotificationListenerEnabled(context)
        }
    }

    // هل كل الإعدادات مكتملة؟
    val allDone = hasNotificationPermission && locationPermissions.allPermissionsGranted

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
                .padding(16.dp)
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

            // بطاقة 1: الإشعارات
            PermissionCard(
                title = "الوصول للإشعارات",
                description = "لقراءة إشعارات التطبيقات وإخبارك بها صوتياً",
                isGranted = hasNotificationPermission,
                onRequest = {
                    PermissionHelper.openNotificationListenerSettings(context)
                },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null
                    )
                }
            )

            // بطاقة 2: الموقع
            PermissionCard(
                title = "صلاحية الموقع",
                description = "لحساب مواقيت الصلاة وإرسال حالة الطقس",
                isGranted = locationPermissions.allPermissionsGranted,
                onRequest = {
                    locationPermissions.launchMultiplePermissionRequest()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null
                    )
                }
            )

            // بطاقة 3: مفتاح API (اختياري)
            PermissionCard(
                title = "مفتاح Gemini API",
                description = "لتفعيل المحادثة الذكية (يمكن تأجيله)",
                isGranted = false,
                onRequest = onOpenApiKeySettings,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null
                    )
                },
                isOptional = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // زر المتابعة
            Button(
                onClick = {
                    appPrefs.setOnboardingComplete(true)
                    appPrefs.setNotificationsEnabled(hasNotificationPermission)
                    appPrefs.setLocationEnabled(locationPermissions.allPermissionsGranted)
                    onComplete()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = allDone
            ) {
                Text(
                    text = if (allDone) "متابعة" else "أكمل الإعدادات أعلاه",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit,
    icon: @Composable () -> Unit,
    isOptional: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted)
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
            icon()
            Spacer(modifier = Modifier.padding(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.padding(8.dp))
            if (isGranted) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "مفعّل",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(onClick = onRequest) {
                    Text("تفعيل")
                }
            }
        }
    }
}
