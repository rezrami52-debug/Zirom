package com.racha.ai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.work.*
import com.racha.ai.core.bubble.RachaBubbleService
import com.racha.ai.core.news.NewsWorker
import com.racha.ai.ui.navigation.AppNavigation
import com.racha.ai.ui.theme.RachaTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkOverlayPermission()
        setupNewsWorker()

        setContent {
            RachaTheme {
                AppNavigation(
                    onToggleBubble = { toggleBubbleService() }
                )
            }
        }
    }

    private fun setupNewsWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val newsRequest = PeriodicWorkRequestBuilder<NewsWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "RachaNewsWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            newsRequest
        )
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // طلب الإذن عند الحاجة
            }
        }
    }

    private fun toggleBubbleService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "يرجى منح إذن العرض فوق التطبيقات لتفعيل الفقاعة", Toast.LENGTH_LONG).show()
            return
        }

        val serviceIntent = Intent(this, RachaBubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "تم تفعيل الفقاعة العائمة بنجاح 🤖", Toast.LENGTH_SHORT).show()
    }
}
