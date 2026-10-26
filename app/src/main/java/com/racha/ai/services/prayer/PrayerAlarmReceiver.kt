package com.racha.ai.services.prayer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.os.PowerManager
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

        fun triggerManually(context: Context, prayerName: String, isPreAlert: Boolean) {
            val prayerKey = PrayerMessagesProvider.getPrayerKey(prayerName)
            val type = if (isPreAlert) "before" else "at_time"

            val content = PrayerMessagesProvider.getMessage(context, prayerKey, type)
                ?: "حان وقت صلاة $prayerName"

            val title = if (isPreAlert) "🕌 اقتراب الأذان — $prayerName"
                        else "🕌 حان وقت صلاة $prayerName"

            sendNotification(context, title, content, prayerName.hashCode())

            TextToSpeechHelper.initialize(context.applicationContext)
            TextToSpeechHelper.speak("حان وقت صلاة $prayerName")
        }

        private fun sendNotification(context: Context, title: String, body: String, id: Int) {
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
                    description = "إشعارات مواقيت الصلاة والأذان"
                    enableVibration(true)
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                    setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI, audioAttributes)
                }
                manager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body.lines().firstOrNull() ?: body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + id, notification)
            } catch (e: SecurityException) {
                Log.e(TAG, "فشل إرسال الإشعار", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(PrayerAlarmScheduler.EXTRA_PRAYER_NAME) ?: return
        val isPreAlert = intent.getBooleanExtra(PrayerAlarmScheduler.EXTRA_IS_PRE_ALERT, false)

        Log.d(TAG, "استلام تنبيه صلاة: $prayerName (pre=$isPreAlert)")

        // حجز WakeLock مؤقت لمنع المعالج من النوم حتى ينتهي الصوت
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "racha:prayer_alarm_wakelock"
        )
        wakeLock?.acquire(15000L) // 15 ثانية كافية

        try {
            val prayerKey = PrayerMessagesProvider.getPrayerKey(prayerName)
            val type = if (isPreAlert) "before" else "at_time"

            val content = PrayerMessagesProvider.getMessage(context, prayerKey, type)
                ?: if (isPreAlert) "باقي 15 دقيقة على صلاة $prayerName"
                   else "حان الآن وقت صلاة $prayerName"

            val title = if (isPreAlert)
                "🕌 اقتراب الأذان — $prayerName"
            else
                "🕌 حان وقت صلاة $prayerName"

            // 1. إرسال الإشعار
            sendNotification(context, title, content, prayerName.hashCode())

            // 2. القراءة الصوتية
            TextToSpeechHelper.initialize(context.applicationContext)
            val speechText = if (isPreAlert)
                "اقترب وقت صلاة $prayerName. توضّأ واستعد."
            else
                "حان الآن وقت صلاة $prayerName."
            TextToSpeechHelper.speak(speechText)
        } finally {
            // يتم تفريغ الـ WakeLock تلقائياً
        }
    }
}
