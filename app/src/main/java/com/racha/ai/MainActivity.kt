package com.racha.ai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.racha.ai.ui.navigation.RachaApp
import com.racha.ai.ui.theme.RachaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // التحقق من الإذن ولكن لن نشغل الخدمة هنا
        checkOverlayPermission()

        setContent {
            RachaTheme {
                RachaApp()
            }
        }
    }

    // تم حذف دالة onUserLeaveHint لمنع تشغيل الفقاعة عند الخروج

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // طلب الإذن عند الحاجة (للاستخدام المستقبلي)
            }
        }
    }
}
