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
import java.util.Calendar

class RoutineAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RachaRoutineAlarm"
        private const val CHANNEL_ID = "racha_routine"
        private const val NOTIFICATION_ID_BASE = 3000

        fun triggerManually(context: Context, item: RoutineItem, isPreAlert: Boolean) {
            val message = buildMessage(context, item, isPreAlert)
            val title = buildTitle(item, isPreAlert)

            sendNotification(context, title, message, item.id.hashCode())
            TextToSpeechHelper.initialize(context.applicationContext)
            TextToSpeechHelper.speak(message)
        }

        /**
         * يبني رسالة ودّية حسب نوع العنصر.
         */
        private fun buildMessage(
            context: Context,
            item: RoutineItem,
            isPreAlert: Boolean
        ): String {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val category = RoutineMessagesProvider.getCategory(item.title)

            // إذا كان النوع معروفاً، استخدم رسالة ودّية
            if (category != null) {
                val subType = RoutineMessagesProvider.getSubTypeByTime(item.title, hour)
                val message = RoutineMessagesProvider.getMessage(context, category, subType)
                if (message != null) {
                    return if (isPreAlert) {
                        "⏰ بعد 10 دقائق:\n$message"
                    } else {
                        message
                    }
                }
            }

            // Fallback
            return if (isPreAlert) {
                "⏰ بعد 10 دقائق: ${item.title}"
            } else {
                "${item.emoji} ${item.title}"
            }
        }

        /**
         * يبني عنوان الإشعار.
         */
        private fun buildTitle(item: RoutineItem, isPreAlert: Boolean): String {
            val emoji = item.emoji
            return if (isPreAlert) {
                "$emoji Racha — تذكير لطيف"
            } else {
                "$emoji Racha"
            }
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
                    description = "تذكيرات ودّية للروتين"
                }
                manager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(body.lines().firstOrNull() ?: body)
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

        val item = RoutineItem(
            id = id,
            title = title,
            emoji = emoji,
            hour = 0, minute = 0
        )

        val message = buildMessage(context, item, isPreAlert)
        val notifTitle = buildTitle(item, isPreAlert)

        sendNotification(context, notifTitle, message, id.hashCode())
        TextToSpeechHelper.initialize(context.applicationContext)
        TextToSpeechHelper.speak(message)
    }
}
