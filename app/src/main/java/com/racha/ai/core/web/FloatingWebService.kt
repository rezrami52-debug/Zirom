package com.racha.ai.core.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

/** A small, user-dismissable overlay browser for explicitly requested web links. */
class FloatingWebService : Service() {
    private lateinit var windowManager: WindowManager
    private var rootView: View? = null
    private var webView: WebView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!startForegroundNotification()) {
            stopSelf()
            return
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rawUrl = intent?.getStringExtra(EXTRA_URL)
        val safeUrl = normalizeHttpUrl(rawUrl) ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        showOrNavigate(safeUrl)
        return START_NOT_STICKY
    }

    private fun showOrNavigate(url: String) {
        webView?.let {
            it.loadUrl(url)
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 6, 6, 6)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        val title = TextView(this).apply {
            text = "Racha Web"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(8, 0, 8, 0)
        }
        val close = Button(this).apply {
            text = "إغلاق"
            setOnClickListener { stopSelf() }
        }
        toolbar.addView(title, LinearLayout.LayoutParams(0, 52, 1f))
        toolbar.addView(close, LinearLayout.LayoutParams(-2, 52))

        val browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val safe = normalizeHttpUrl(request.url.toString()) ?: return true
                    view.loadUrl(safe)
                    return true
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    val safe = normalizeHttpUrl(url) ?: return true
                    view.loadUrl(safe)
                    return true
                }
            }
            webChromeClient = WebChromeClient()
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, 60))
        root.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))

        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            (resources.displayMetrics.heightPixels * 0.78f).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(root, params)
            rootView = root
            webView = browser
            browser.loadUrl(url)
        } catch (_: Exception) {
            browser.destroy()
            stopSelf()
        }
    }

    private fun startForegroundNotification(): Boolean {
        val channelId = "floating_web_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "Racha Web", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Racha Web")
            .setContentText("نافذة ويب عائمة قيد التشغيل")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onDestroy() {
        try {
            rootView?.let { windowManager.removeView(it) }
        } catch (_: Exception) {
        }
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        webView = null
        rootView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_OPEN_URL = "com.racha.ai.action.OPEN_URL"
        const val EXTRA_URL = "com.racha.ai.extra.URL"
        private const val NOTIFICATION_ID = 3003

        fun normalizeHttpUrl(raw: String?): String? {
            val value = raw?.trim()?.take(2_000) ?: return null
            val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase()
            return if ((scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()) {
                uri.toString()
            } else {
                null
            }
        }
    }
}
