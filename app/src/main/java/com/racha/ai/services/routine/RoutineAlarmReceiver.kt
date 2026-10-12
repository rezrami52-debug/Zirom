package com.racha.ai.services.routine

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
import com.racha.ai.data.local.RoutineItem
import com.racha.ai.services.tts.TextToSpeechHelper

class RoutineAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RachaRoutineAlarm"
        private const val CHANNEL_ID = "racha_routine"
        private const val NOTIFICATION_ID_BASE = 3000

        fun triggerManually(context: Context, item: RoutineItem, isPreAlert: Boolean) {
            val (title, body) = if (isPreAlert) {
                "⏰ اقتراب موعد (اختبار)" to "باقي 10 دقائق على: ${item.title}"
            } else {
                "${item.emoji} حان الوقت (اختبار)" to "حان وقت: ${item.title}"
            }
            sendNotification(context, title, body, item.id.hashCode())
            TextToSpeechHelper.initialize(context.applicationContext)
            TextToSpeechHelper.speak(body)
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
                    "روتيني اليومي",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "تذكيرات الروتين اليومي"
                }
                manager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
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

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(RoutineAlarmScheduler.EXTRA_ROUTINE_TITLE) ?: return
        val emoji = intent.getStringExtra(RoutineAlarmScheduler.EXTRA_ROUTINE_EMOJI) ?: "⏰"
        val id = intent.getStringExtra(RoutineAlarmScheduler.EXTRA_ROUTINE_ID) ?: return
        val isPreAlert = intent.getBooleanExtra(RoutineAlarmScheduler.EXTRA_IS_PRE_ALERT, false)

        val (nTitle, body) = if (isPreAlert) {
            "⏰ اقتراب موعد" to "باقي 10 دقائق على: $title"
        } else {
            "$emoji حان الوقت" to "حان وقت: $title"
        }

        sendNotification(context, nTitle, body, id.hashCode())
        TextToSpeechHelper.initialize(context.applicationContext)
        TextToSpeechHelper.speak(body)
    }
}
