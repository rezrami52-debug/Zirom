package com.racha.ai.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import com.racha.ai.core.overlay.OverlayLifecycleOwner
import kotlin.math.roundToInt

class FloatingTvService : Service() {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private val lifecycleOwner = OverlayLifecycleOwner()
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val currentChannelName = mutableStateOf("بث مباشر")
    private val currentStreamUrl = mutableStateOf("")
    private var isExpanded = false

    companion object {
        const val EXTRA_CHANNEL_NAME = "EXTRA_CHANNEL_NAME"
        const val EXTRA_STREAM_URL = "EXTRA_STREAM_URL"
        const val ACTION_STOP_TV = "ACTION_STOP_TV"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_TV) {
            stopSelf()
            return START_NOT_STICKY
        }

        val channel = intent?.getStringExtra(EXTRA_CHANNEL_NAME) ?: "بث مباشر"
        val url = intent?.getStringExtra(EXTRA_STREAM_URL) ?: ""

        if (url.isNotBlank()) {
            currentChannelName.value = channel
            currentStreamUrl.value = url

            if (composeView == null) {
                setupFloatingTvWindow()
            }
        }

        return START_NOT_STICKY
    }

    private fun setupFloatingTvWindow() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val defaultWidth = (300 * density).toInt()
        val defaultHeight = (195 * density).toInt()

        layoutParams = WindowManager.LayoutParams(
            defaultWidth,
            defaultHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        composeView = ComposeView(this).apply {
            lifecycleOwner.attachToComposeView(this)
            setContent {
                TvPlayerContent(
                    channelName = currentChannelName.value,
                    streamUrl = currentStreamUrl.value,
                    onDrag = { dx, dy -> updateTvPosition(dx, dy) },
                    onToggleResize = { toggleTvResize() },
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

    private fun updateTvPosition(dx: Float, dy: Float) {
        layoutParams.x += dx.roundToInt()
        layoutParams.y += dy.roundToInt()
        try {
            windowManager.updateViewLayout(composeView, layoutParams)
        } catch (_: Exception) {}
    }

    private fun toggleTvResize() {
        val dm = resources.displayMetrics
        val density = dm.density
        if (!isExpanded) {
            layoutParams.width = (dm.widthPixels * 0.94f).toInt()
            layoutParams.height = (layoutParams.width * 9 / 16) + (34 * density).toInt()
            isExpanded = true
        } else {
            layoutParams.width = (300 * density).toInt()
            layoutParams.height = (195 * density).toInt()
            isExpanded = false
        }
        try {
            windowManager.updateViewLayout(composeView, layoutParams)
        } catch (_: Exception) {}
    }

    private fun startForegroundServiceNotification() {
        try {
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
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(3003, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(3003, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        composeView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { e.printStackTrace() }
            composeView = null
        }
        lifecycleOwner.handleDestroy()
    }
}
