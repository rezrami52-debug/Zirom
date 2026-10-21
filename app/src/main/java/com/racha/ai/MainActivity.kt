package com.racha.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.racha.ai.ui.navigation.RachaApp
import com.racha.ai.ui.theme.RachaTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "✅ تم تفعيل الإشعارات", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "⚠️ الإشعارات معطلة. قد لا تعمل الفقاعة.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // طلب إذن الإشعارات (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // التحقق من إذن Overlay (بدون طلب تلقائي)
        checkOverlayPermission()

        setContent {
            RachaTheme {
                RachaApp()
            }
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // طلب الإذن عند الحاجة (للاستخدام المستقبلي)
            }
        }
    }
}
