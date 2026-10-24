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
import java.util.Locale
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import com.racha.ai.core.overlay.OverlayLifecycleOwner
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.data.api.GeminiService
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
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ar")
            }
        }
        setupFloatingBubble()
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
                            tts?.speak("في وضع السبات سيدي", TextToSpeech.QUEUE_FLUSH, null, null)
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

    /**
     * إطلاق حلقة المراقبة الحية أثناء استيقاظ EVE
     */
    private fun startContinuousScreenObservation() {
        val accessibility = EveAccessibilityService.instance
        if (accessibility == null) {
            tts?.speak("سيدي، يرجى تفعيل إمكانية الوصول لـ Racha في الإعدادات لأتمكن من قراءة الشاشة معك", TextToSpeech.QUEUE_FLUSH, null, null)
            return
        }

        tts?.speak("أنا مستيقظة وأراقب معك الشاشة سيدي", TextToSpeech.QUEUE_FLUSH, null, null)

        screenObserverJob?.cancel()
        screenObserverJob = serviceScope.launch(Dispatchers.IO) {
            val securePrefs = SecurePreferences(this@EveBubbleService)
            val apiKey = securePrefs.getApiKey()

            if (apiKey.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    tts?.speak("سيدي، يرجى تعيين مفتاح Gemini API في الإعدادات أولاً", TextToSpeech.QUEUE_FLUSH, null, null)
                }
                return@launch
            }

            val gemini = GeminiService(apiKey)

            // فحص دوري كل 10 ثوانٍ طالما EVE مستيقظة
            while (isActive) {
                delay(6000) // انتظار 6 ثوانٍ بعد فتح التطبيق أو التمرير

                val snapshot = accessibility.getLatestScreenSnapshot()
                if (snapshot != null && snapshot.visibleText.length > 30) {
                    val currentHash = (snapshot.appName + snapshot.visibleText.take(150)).hashCode()

                    // إذا كان المحتوى جديداً ومختلفاً عما تحدثت عنه سابقاً
                    if (currentHash != lastSpokenContentHash) {
                        lastSpokenContentHash = currentHash
                        
                        try {
                            val aiSuggestion = gemini.analyzeScreenContext(snapshot.appName, snapshot.visibleText)
                            withContext(Dispatchers.Main) {
                                tts?.speak(aiSuggestion, TextToSpeech.QUEUE_ADD, null, null)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        // راحة لمدة 20 ثانية بعد التحدث حتى لا تزعج المستخدم بكثرة الكلام
                        delay(20000)
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
