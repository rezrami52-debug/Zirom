package com.racha.ai.services.sleep

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.services.tts.TextToSpeechHelper
import java.util.Calendar

class SleepGuardianWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RachaSleepGuardian"
        private const val CHANNEL_ID = "racha_sleep_guardian"
        private const val NOTIFICATION_ID = 5001

        // ✅ بدون private — للوصول من Scheduler
        const val WORK_NAME = "racha_sleep_guardian_worker"

        private const val USAGE_WINDOW_MINUTES = 15
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = AppPreferences(context)

        if (!prefs.isSleepGuardianEnabled()) {
            Log.d(TAG, "معطّل")
            return Result.success()
        }

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val startHour = prefs.getSleepGuardianStartHour()

        val isNightTime = currentHour >= startHour || currentHour < 4
        if (!isNightTime) {
            Log.d(TAG, "ليس وقت النوم (الساعة: $currentHour)")
            return Result.success()
        }

        val todayStart = getTodayStartMillis()
        val lastSent = prefs.getLastSleepGuardianSentDate()
        if (lastSent > todayStart) {
            Log.d(TAG, "أُرسل مسبقاً الليلة")
            return Result.success()
        }

        if (!isPhoneInUse(context)) {
            Log.d(TAG, "الهاتف غير مستخدم — لا نُرسل")
            return Result.success()
        }

        sendSleepReminder(context, currentHour)
        prefs.setLastSleepGuardianSentDate(System.currentTimeMillis())

        return Result.success()
    }

    private fun isPhoneInUse(context: Context): Boolean {
        return try {
            val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return false

            val endTime = System.currentTimeMillis()
            val startTime = endTime - (USAGE_WINDOW_MINUTES * 60 * 1000L)

            val stats = usageStats.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (stats.isNullOrEmpty()) return false

            val recentThreshold = endTime - (5 * 60 * 1000L)
            stats.any { it.lastTimeUsed > recentThreshold }
        } catch (e: Exception) {
            Log.e(TAG, "فشل قراءة UsageStats", e)
            false
        }
    }

    private fun sendSleepReminder(context: Context, currentHour: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val message = when (currentHour) {
            in 22..23 -> "🌙 أراك ما زلت مستيقظاً...\nغداً يوم حافل. هل نستعد للنوم؟"
            in 0..1 -> "🌙 Racha: الوقت متأخر...\nغداً ينتظرك، نم قليلاً 💙"
            in 2..3 -> "🌙 ما زلت مستيقظاً؟\nالراحة الآن أفضل من غداً متعباً 🌙"
            else -> "🌙 وقت الراحة..."
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "حارس النوم",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تذكيرات لطيفة بالراحة"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🌙 Racha")
            .setContentText(message.lines().firstOrNull() ?: message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "أُرسل تذكير النوم")
        } catch (e: SecurityException) {
            Log.e(TAG, "فشل إرسال الإشعار", e)
        }

        TextToSpeechHelper.initialize(context.applicationContext)
        TextToSpeechHelper.speak("أراك ما زلت مستيقظاً. غداً يوم حافل، نم مرتاحاً.")
    }

    private fun getTodayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
