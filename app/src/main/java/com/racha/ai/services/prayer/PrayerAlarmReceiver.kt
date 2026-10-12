package com.racha.ai.services.prayer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.racha.ai.services.tts.TextToSpeechHelper

class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RachaPrayerAlarm"
        private const val CHANNEL_ID = "racha_prayer"
        private const val NOTIFICATION_ID_BASE = 2000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME) ?: return
        val isPreAlert = intent.getBooleanExtra(PrayerAlarmScheduler.EXTRA_IS_PRE_ALERT, false)

        Log.d(TAG, "استلم: $prayerName (pre=$isPreAlert)")

        val (title, body) = if (isPreAlert) {
            "🕌 اقتراب الأذان" to "باقي 15 دقيقة على صلاة $prayerName"
        } else {
            "🕌 حان وقت الأذان" to "حان الآن وقت صلاة $prayerName"
        }

        // 1. إشعار كتابي
        sendNotification(context, title, body, prayerName.hashCode())

        // 2. إشعار صوتي
        TextToSpeechHelper.initialize(context.applicationContext)
        TextToSpeechHelper.speak(body)
    }

    private fun sendNotification(context: Context, title: String, body: String, id: Int) {
        // صلاحية الإشعارات (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "صلاحية الإشعارات غير مفعّلة")
                return
            }
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "مواقيت الصلاة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات مواقيت الصلاة"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + id, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "فشل إرسال الإشعار", e)
        }
    }
}
