package com.racha.ai.core.faceauth

import android.os.Build
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun BiometricLockScreen(
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("يجب التحقق من هويتك لفتح Racha") }
    var hasPrompted by remember { mutableStateOf(false) }

    // تشغيل Prompt تلقائياً عند الظهور
    LaunchedEffect(Unit) {
        if (hasPrompted) return@LaunchedEffect
        hasPrompted = true

        if (context !is FragmentActivity) {
            statusText = "⚠️ خطأ: نشاط غير مدعوم"
            return@LaunchedEffect
        }

        // تحقق من توفر البصمة/الوجه
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

        // إعداد Prompt
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
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    statusText = "❌ لم يتم التعرف. حاول مرة أخرى."
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
