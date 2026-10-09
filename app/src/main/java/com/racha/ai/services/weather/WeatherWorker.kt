package com.racha.ai.services.weather

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.racha.ai.services.location.LocationHelper
import com.racha.ai.services.tts.TextToSpeechHelper

/**
 * Worker يُشغَّل كل 6 ساعات لجلب الطقس وإرسال إشعار.
 */
class WeatherWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        // تحقق من صلاحية الموقع
        val hasLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocation) return Result.failure()

        // جلب الموقع
        val location = LocationHelper.getCurrentLocation(context) ?: return Result.retry()

        // جلب الطقس
        val weather = WeatherService.fetchWeather(
            latitude = location.latitude,
            longitude = location.longitude,
            cityName = location.cityName ?: ""
        ) ?: return Result.retry()

        // إرسال إشعار
        sendNotification(context, weather.fullArabicDescription())

        // قراءة صوتية
        TextToSpeechHelper.speak(weather.fullArabicDescription())

        return Result.success()
    }

    private fun sendNotification(context: Context, text: String) {
        // تحقق من صلاحية الإشعارات (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val channelId = "racha_weather"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "حالة الطقس",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات حالة الطقس"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("🌤️ حالة الطقس")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // تجاهل
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME = "racha_weather_worker"
    }
}
