package com.racha.ai.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class FloatingMediaService : Service() {

    private lateinit var mediaWindowManager: MediaWindowManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        mediaWindowManager = MediaWindowManager(this)
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mediaUriString = intent?.getStringExtra(EXTRA_MEDIA_URI)
        val title = intent?.getStringExtra(EXTRA_MEDIA_TITLE) ?: "مقطع وسائط"

        if (!mediaUriString.isNullOrEmpty()) {
            val uri = Uri.parse(mediaUriString)
            mediaWindowManager.createMediaWindow(uri, title)
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "floating_media_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Racha AI Media Player", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("مشغل الوسائط العائم")
            .setContentText("النوافذ المتعددة قيد التشغيل")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(2002, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaWindowManager.releaseAll()
    }

    companion object {
        const val EXTRA_MEDIA_URI = "EXTRA_MEDIA_URI"
        const val EXTRA_MEDIA_TITLE = "EXTRA_MEDIA_TITLE"
    }
}
