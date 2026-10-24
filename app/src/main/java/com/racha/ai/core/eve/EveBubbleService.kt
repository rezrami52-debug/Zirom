package com.racha.ai.core.eve

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import com.racha.ai.core.overlay.OverlayLifecycleOwner
import kotlinx.coroutines.*

class EveBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private val overlayLifecycleOwner = OverlayLifecycleOwner()
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var tts: TextToSpeech? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var screenObserverJob: Job? = null
    private var lastSpokenContentHash: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()
        setupHighQualityArabicTts()
        setupFloatingBubble()
    }

    /**
     * إعداد صوت عربي أنثوي فائق الجودة متناسق مع شخصية EVE
     */
    private fun setupHighQualityArabicTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val arabicLocale = Locale("ar")
                tts?.language = arabicLocale

                // محاولة اختيار أفضل صوت عربي متاح في نظام الهاتف
                try {
                    val highQualityVoice = tts?.voices?.firstOrNull { voice ->
                        voice.locale.language == "ar" && 
                        !voice.isNetworkConnectionRequired &&
                        (voice.quality == Voice.QUALITY_VERY_HIGH || voice.quality == Voice.QUALITY_HIGH || voice.name.contains("female", ignoreCase = true))
                    }
                    if (highQualityVoice != null) {
                        tts?.voice = highQualityVoice
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // ضبط نبرة الصوت وسرعته ليصبح ناعماً كصوت EVE وليس صوت آلة خشن
                tts?.setPitch(1.08f)      // نبرة أنثوية أرق قليلاً
                tts?.setSpeechRate(0.92f) // سرعة هادئة ومتزنة تمنع التلعثم في مخارج الحروف
            }
        }
    }

    /**
     * تنظيف النص العربي من الإيموجي والرموز قبل النطق لتفادي التلعثم
     */
    private fun speakCleanText(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        val clean = text
            .replace(Regex("[\\p{So}\\p{Cn}]"), "") // إزالة كل الإيموجي والرموز
            .replace(Regex("[\\*\\_\\#\\`\\~]"), "") // إزالة علامات Markdown
            .replace("  ", " ")
            .trim()

        if (clean.isNotBlank()) {
            tts?.speak(clean, queueMode, null, "EVE_SPEECH_${System.currentTimeMillis()}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRIGGER_NOTIFICATION_PULSE -> EveState.addNotification()
            ACTION_STOP_SERVICE -> stopSelf()
        }
        return START_STICKY
    }

    private fun setupFloatingBubble() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        composeView = ComposeView(this).apply {
            overlayLifecycleOwner.attachToComposeView(this)
            setContent {
                EveBubbleContent(
                    onToggleSleepWake = { isAwake ->
                        if (isAwake) {
                            startContinuousScreenObservation()
                        } else {
                            stopContinuousScreenObservation()
                            speakCleanText("في وضع السبات سيدي")
                        }
                    },
                    onOpenApp = { handleEveClick() },
                    onDismiss = {
                        stopContinuousScreenObservation()
                        composeView?.let { view ->
                            try { windowManager.removeView(view) } catch (e: Exception) { e.printStackTrace() }
                            composeView = null
                        }
                        stopSelf()
                    },
                    onDrag = { dx, dy -> updatePosition(dx, dy) }
                )
            }
        }

        try {
            windowManager.addView(composeView, layoutParams)
            overlayLifecycleOwner.handleResume()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun startContinuousScreenObservation() {
        val accessibility = EveAccessibilityService.instance
        if (accessibility == null) {
            speakCleanText("سيدي، يرجى تفعيل إمكانية الوصول لـ Racha في الإعدادات لأتمكن من قراءة الشاشة معك")
            return
        }

        speakCleanText("أنا مستيقظة وأراقب معك الشاشة سيدي")

        screenObserverJob?.cancel()
        screenObserverJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(6000)

                val snapshot = accessibility.getLatestScreenSnapshot()
                if (snapshot != null && snapshot.visibleText.length > 20) {
                    val currentHash = (snapshot.appName + snapshot.visibleText.take(120)).hashCode()

                    if (currentHash != lastSpokenContentHash) {
                        lastSpokenContentHash = currentHash
                        val smartInsight = LocalScreenAnalyzer.analyze(snapshot)

                        withContext(Dispatchers.Main) {
                            speakCleanText(smartInsight, TextToSpeech.QUEUE_ADD)
                        }

                        delay(18000)
                    }
                }
            }
        }
    }

    private fun stopContinuousScreenObservation() {
        screenObserverJob?.cancel()
        screenObserverJob = null
        lastSpokenContentHash = 0
    }

    private fun updatePosition(dx: Float, dy: Float) {
        layoutParams.x += dx.toInt()
        layoutParams.y += dy.toInt()
        try {
            windowManager.updateViewLayout(composeView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleEveClick() {
        EveState.setMood(EveMood.AWAKE_SMILING)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(launchIntent)
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "eve_bubble_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Racha AI (EVE)", NotificationManager.IMPORTANCE_MIN)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Racha AI (EVE)")
            .setContentText("المساعد الذكي نشط وجاهز للطفو")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopContinuousScreenObservation()
        tts?.stop()
        tts?.shutdown()
        overlayLifecycleOwner.handleDestroy()
        composeView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    companion object {
        const val ACTION_TRIGGER_NOTIFICATION_PULSE = "ACTION_TRIGGER_NOTIFICATION_PULSE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
    }
}
