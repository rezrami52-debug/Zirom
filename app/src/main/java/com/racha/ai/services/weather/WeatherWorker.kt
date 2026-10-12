package com.racha.ai.services.weather

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.racha.ai.services.location.LocationHelper
import com.racha.ai.services.tts.TextToSpeechHelper

class WeatherWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RachaWeatherWorker"
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME = "racha_weather_worker"
        private const val CHANNEL_ID = "racha_weather"

        /**
         * إرسال إشعار اختباري فوري.
         */
        suspend fun triggerManually(context: Context): Boolean {
            val hasLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasLocation) {
                Log.w(TAG, "لا توجد صلاحية الموقع")
                return false
            }

            val location = LocationHelper.getCurrentLocation(context)
            if (location == null) {
                Log.w(TAG, "فشل تحديد الموقع")
                return false
            }

            val weather = WeatherService.fetchWeather(
                latitude = location.latitude,
                longitude = location.longitude,
                cityName = location.cityName ?: ""
            )
            if (weather == null) {
                Log.w(TAG, "فشل جلب الطقس")
                return false
            }

            sendNotification(context, weather.fullArabicDescription())
            TextToSpeechHelper.initialize(context.applicationContext)
            TextToSpeechHelper.speak(weather.fullArabicDescription())
            return true
        }

        private fun sendNotification(context: Context, text: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "حالة الطقس",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "إشعارات حالة الطقس"
                }
                manager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("🌤️ حالة الطقس (اختبار)")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                Log.e(TAG, "فشل إرسال الإشعار", e)
            }
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext

        val hasLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocation) return Result.failure()

        val location = LocationHelper.getCurrentLocation(context) ?: return Result.retry()

        val weather = WeatherService.fetchWeather(
            latitude = location.latitude,
            longitude = location.longitude,
            cityName = location.cityName ?: ""
        ) ?: return Result.retry()

        sendNotification(context, weather.fullArabicDescription())
        TextToSpeechHelper.speak(weather.fullArabicDescription())

        return Result.success()
    }
}
