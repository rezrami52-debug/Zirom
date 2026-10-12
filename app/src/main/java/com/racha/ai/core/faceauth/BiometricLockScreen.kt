package com.racha.ai.core.faceauth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.data.repository.LockAttemptRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BiometricLockScreen(
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var statusText by remember { mutableStateOf("يجب التحقق من هويتك لفتح Racha") }
    var hasPrompted by remember { mutableStateOf(false) }
    var failureCount by remember { mutableStateOf(0) }

    val appPrefs = remember { AppPreferences(context) }
    val attemptRepo = remember { LockAttemptRepository(context) }

    LaunchedEffect(Unit) {
        // تنظيف الصور القديمة
        CoroutineScope(Dispatchers.IO).launch {
            attemptRepo.cleanupOld(appPrefs.getIntruderRetentionDays())
        }

        if (hasPrompted) return@LaunchedEffect
        hasPrompted = true

        if (context !is FragmentActivity) {
            statusText = "⚠️ خطأ: نشاط غير مدعوم"
            return@LaunchedEffect
        }

        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                             BiometricManager.Authenticators.BIOMETRIC_WEAK

        val canAuthenticate = biometricManager.canAuthenticate(authenticators)

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            statusText = when (canAuthenticate) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                    "⚠️ الجهاز لا يدعم البصمة أو الوجه"
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                    "⚠️ مستشعر البصمة غير متاح حالياً"
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                    "⚠️ لم تسجّل بصمة/وجه في إعدادات الهاتف"
                else -> "⚠️ خطأ في المصادقة"
            }
            return@LaunchedEffect
        }

        val executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("🔒 Racha")
            .setSubtitle("التحقق من هويتك")
            .setDescription("استخدم بصمتك أو وجهك")
            .setAllowedAuthenticators(authenticators)
            .setNegativeButtonText("إلغاء")
            .build()

        val prompt = BiometricPrompt(
            context,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    statusText = "✅ تم التحقق"
                    // تسجيل النجاح
                    CoroutineScope(Dispatchers.IO).launch {
                        attemptRepo.recordAttempt(success = true)
                    }
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    failureCount++
                    statusText = "❌ لم يتم التعرف. حاول مرة أخرى ($failureCount)"

                    // ✅ هل وصلنا للحد الأدنى؟
                    val threshold = appPrefs.getIntruderThreshold()
                    val intruderEnabled = appPrefs.isIntruderPhotoEnabled()

                    if (intruderEnabled && failureCount == threshold) {
                        // التقاط صورة المتسلل
                        statusText = "🔍 جاري التحقق من المحيط..."

                        IntruderPhotoCapture.capture(
                            context = context,
                            lifecycleOwner = lifecycleOwner
                        ) { file ->
                            CoroutineScope(Dispatchers.IO).launch {
                                attemptRepo.recordAttempt(
                                    success = false,
                                    photoPath = file?.absolutePath
                                )
                            }
                        }
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    statusText = "⚠️ $errString"
                }
            }
        )

        prompt.authenticate(promptInfo)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🔒",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Racha محمي",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إغلاق التطبيق")
            }
        }
    }
}
