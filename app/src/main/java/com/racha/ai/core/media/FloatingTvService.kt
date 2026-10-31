package com.racha.ai.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import com.racha.ai.core.overlay.OverlayLifecycleOwner

class FloatingTvService : Service() {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var lifecycleOwner: OverlayLifecycleOwner
    private lateinit var layoutParams: WindowManager.LayoutParams

    companion object {
        const val EXTRA_CHANNEL_NAME = "EXTRA_CHANNEL_NAME"
        const val EXTRA_STREAM_URL = "EXTRA_STREAM_URL"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME) ?: "قناة"
        val streamUrl = intent?.getStringExtra(EXTRA_STREAM_URL)

        if (streamUrl != null && composeView == null) {
            setupTvPlayer(channelName, streamUrl)
        }
        return START_NOT_STICKY
    }

    private fun setupTvPlayer(channelName: String, streamUrl: String) {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            dpToPx(320), dpToPx(220),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100; y = 300
        }

        lifecycleOwner = OverlayLifecycleOwner()

        composeView = ComposeView(this).apply {
            lifecycleOwner.attachToComposeView(this)
            setContent {
                TvPlayerContent(
                    channelName = channelName,
                    streamUrl = streamUrl,
                    onDrag = { dx, dy ->
                        layoutParams.x += dx.toInt()
                        layoutParams.y += dy.toInt()
                        windowManager.updateViewLayout(this@apply, layoutParams)
                    },
                    onToggleResize = {
                        if (layoutParams.width == dpToPx(320)) {
                            layoutParams.width = dpToPx(480)
                            layoutParams.height = dpToPx(300)
                        } else {
                            layoutParams.width = dpToPx(320)
                            layoutParams.height = dpToPx(220)
                        }
                        windowManager.updateViewLayout(this@apply, layoutParams)
                    },
                    onClose = { stopSelf() }
                )
            }
        }

        try {
            windowManager.addView(composeView, layoutParams)
            lifecycleOwner.handleResume()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "floating_tv_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Racha TV Player", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("مشغل القنوات العائم")
            .setContentText("التلفزيون يعمل في الخلفية")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(3003, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        composeView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { e.printStackTrace() }
        }
        lifecycleOwner.handleDestroy()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
