package com.racha.ai.core.faceauth

import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun BiometricLockScreen(
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    onBypass: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startBiometricAuth(context, 
            onAuthSuccess = onSuccess,
            onAuthFailed = { errorMessage = "فشل التحقق. حاول مرة أخرى." },
            onError = { errorMessage = it },
            onStateChanged = { isAuthenticating = it }
        )
    }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "🔒", fontSize = 80.sp, modifier = Modifier.padding(bottom = 24.dp))
        Text(text = "Racha محمي", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

        if (errorMessage != null) {
            Text(text = "⚠️ $errorMessage", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 24.dp))
        } else if (isAuthenticating) {
            Text(text = "جاري التحقق من هويتك...", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 24.dp))
        } else {
            Text(text = "استخدم بصمة الإصبع أو وجهك لفتح التطبيق", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 24.dp))
        }

        Button(
            onClick = {
                errorMessage = null
                startBiometricAuth(context, onAuthSuccess = onSuccess, onAuthFailed = { errorMessage = "فشل التحقق." }, onError = { errorMessage = it }, onStateChanged = { isAuthenticating = it })
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isAuthenticating
        ) {
            Text("إعادة المحاولة")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (onBypass != null) {
            OutlinedButton(onClick = onBypass, modifier = Modifier.fillMaxWidth()) {
                Text("الدخول بدون تحقق (غير آمن)")
            }
        } else {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("إلغاء")
            }
        }
    }
}

private fun startBiometricAuth(
    context: android.content.Context,
    onAuthSuccess: () -> Unit,
    onAuthFailed: (String) -> Unit,
    onError: (String) -> Unit,
    onStateChanged: (Boolean) -> Unit
) {
    try {
        val activity = context as? FragmentActivity ?: run { onError("خطأ في السياق"); return }

        val biometricManager = BiometricManager.from(context)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> { /* متابعة */ }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> { onError("لا يوجد مستشعر بيومتري"); return }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> { onError("المستشعر غير متاح حالياً"); return }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> { onError("لم تقم بتسجيل بصمة أو وجه في إعدادات الهاتف"); return }
            else -> { onError("البيومترية غير مدعومة"); return }
        }

        val executor = ContextCompat.getMainExecutor(context)
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("التحقق من الهوية")
            .setSubtitle("افتح Racha")
            .setNegativeButtonText("إلغاء")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfoBuilder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            promptInfoBuilder.setDeviceCredentialAllowed(true)
        }

        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onStateChanged(false)
                    onAuthSuccess()
                }
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onStateChanged(false)
                    onAuthFailed("فشل التحقق.")
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onStateChanged(false)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onError(errString.toString())
                    }
                }
            }
        )

        onStateChanged(true)
        biometricPrompt.authenticate(promptInfoBuilder.build())
    } catch (e: Exception) {
        Log.e("BiometricLock", "خطأ: ${e.message}", e)
        onError("خطأ: ${e.message ?: "غير معروف"}")
    }
}
