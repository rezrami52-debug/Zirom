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
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.data.api.GeminiService
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
        val securePrefs = SecurePreferences(context)
        val apiKey = securePrefs.getApiKey()

        if (apiKey.isNullOrBlank()) {
            return@withContext Result.success()
        }

        val sharedPrefs = context.getSharedPreferences("proactive_prefs", Context.MODE_PRIVATE)
        val lastSent = sharedPrefs.getLong(PREF_LAST_NEWS_TIME, 0L)
        val now = System.currentTimeMillis()

        if (now - lastSent < MIN_INTERVAL_MILLIS) {
            return@withContext Result.success()
        }

        try {
            val headlines = NewsFetcher.fetchTopHeadlines(4)
            if (headlines.isNotEmpty()) {
                val gemini = GeminiService(apiKey)
                val summary = gemini.summarizeNewsAsJarvis(headlines)

                showJarvisNotification(summary)
                EveState.addNotification()

                sharedPrefs.edit().putLong(PREF_LAST_NEWS_TIME, now).apply()
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
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
