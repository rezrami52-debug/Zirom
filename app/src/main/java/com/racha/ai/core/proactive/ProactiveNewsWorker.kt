package com.racha.ai.core.proactive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.racha.ai.R
import com.racha.ai.core.eve.EveState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProactiveNewsWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "jarvis_proactive_channel"
        const val PREF_LAST_NEWS_TIME = "last_proactive_news_time"
        private const val MIN_INTERVAL_MILLIS = 3 * 60 * 60 * 1000L // كل 3 ساعات على الأقل
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sharedPrefs = context.getSharedPreferences("proactive_prefs", Context.MODE_PRIVATE)
        val lastSent = sharedPrefs.getLong(PREF_LAST_NEWS_TIME, 0L)
        val now = System.currentTimeMillis()

        if (now - lastSent < MIN_INTERVAL_MILLIS) {
            return@withContext Result.success()
        }

        try {
            val headlines = NewsFetcher.fetchTopHeadlines(4)
            if (headlines.isEmpty()) return@withContext Result.success()

            // Use only source headlines with verified publication timestamps; do not ask an LLM
            // to invent a narrative that could make an old event sound current.
            val brief = NewsFetcher.formatBrief(headlines)
            showJarvisNotification(brief)
            EveState.addNotification()
            sharedPrefs.edit().putLong(PREF_LAST_NEWS_TIME, System.currentTimeMillis()).apply()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("ProactiveNewsWorker", "تعذر إنشاء إشعار الأخبار", e)
            Result.retry()
        }
    }

    private fun showJarvisNotification(message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "J.A.R.V.I.S. الاستباقية",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات JARVIS الذكية عند توفر الأخبار والأحداث"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("J.A.R.V.I.S. • موجز إخباري")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(101, notification)
    }
}
