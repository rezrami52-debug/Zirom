package com.racha.ai.core.eve

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.*
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.racha.ai.core.apps.AppResolver
import com.racha.ai.core.apps.IntentHandler
import com.racha.ai.core.contacts.ContactResolver
import com.racha.ai.core.media.MediaItemData
import com.racha.ai.core.overlay.OverlayLifecycleOwner
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.core.voice.VoiceInputHelper
import com.racha.ai.data.api.GeminiService
import kotlinx.coroutines.*
import java.util.Locale

class EveBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private val overlayLifecycleOwner = OverlayLifecycleOwner()
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var tts: TextToSpeech? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var voiceInputHelper: VoiceInputHelper? = null
    private lateinit var intentHandler: IntentHandler
    private lateinit var securePreferences: SecurePreferences

    private var exoPlayer: ExoPlayer? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var hasAlertedFullCharge = false
    private var hasAlertedLowBattery = false

    private var isHandlingAwake = false
    private var isContinuousListening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        voiceInputHelper = VoiceInputHelper(this)
        securePreferences = SecurePreferences(this)
        intentHandler = IntentHandler(this, AppResolver(this), ContactResolver(this))
        exoPlayer = ExoPlayer.Builder(this).build()

        startForegroundServiceNotification()
        setupHighQualityArabicTts()
        setupFloatingBubble()
        setupScreenStateReceiver()
        setupBatteryGuardianReceiver()
    }

    private fun setupHighQualityArabicTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ar")
                try {
                    val highQualityVoice = tts?.voices?.firstOrNull { voice ->
                        voice.locale.language == "ar" && !voice.isNetworkConnectionRequired &&
                        (voice.quality == Voice.QUALITY_VERY_HIGH || voice.name.contains("female", ignoreCase = true))
                    }
                    if (highQualityVoice != null) tts?.voice = highQualityVoice
                } catch (e: Exception) { e.printStackTrace() }
                tts?.setPitch(1.08f)
                tts?.setSpeechRate(0.95f)

                // الاستماع التلقائي بعد انتهاء الرد الصوتي (الحوار المستمر)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "CONTINUE_CONVERSATION") {
                            serviceScope.launch(Dispatchers.Main) {
                                delay(300)
                                triggerDirectVoiceListening()
                            }
                        }
                    }
                    override fun onError(utteranceId: String?) {}
                })
            }
        }
    }

    private fun speakCleanText(text: String, continueConversation: Boolean = false) {
        val clean = text.replace(Regex("[\\p{So}\\p{Cn}\\*\\_\\#]"), "").trim()
        if (clean.isNotBlank()) {
            val utteranceId = if (continueConversation) "CONTINUE_CONVERSATION" else "SINGLE_STATEMENT"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRIGGER_NOTIFICATION_PULSE -> EveState.addNotification()
            ACTION_STOP_SERVICE -> stopSelf()
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 280
        }

        composeView = ComposeView(this).apply {
            overlayLifecycleOwner.attachToComposeView(this)

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    handleTapOutside()
                    true
                } else false
            }

            setContent {
                EveBubbleContent(
                    onToggleSleepWake = { isAwake ->
                        if (isAwake) {
                            speakCleanText("نعم سيدي، أنا أستمع إليك")
                            triggerDirectVoiceListening()
                        } else {
                            speakCleanText("في وضع السبات سيدي")
                            stopListening()
                        }
                    },
                    onOpenApp = { handleEveClick() },
                    onDismiss = { stopSelf() },
                    onMediaPlayPause = { togglePlayPause() },
                    onMediaNext = { skipNext() },
                    onMediaPrev = { skipPrev() },
                    onMediaItemSelected = { item -> playSelectedMedia(item) },
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

    private fun handleTapOutside() {
        EveState.closeShelf()
        EveState.setMood(EveMood.SLEEPING)
        stopListening()
    }

    private fun playSelectedMedia(item: MediaItemData) {
        if (item.type == "AUDIO") {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            exoPlayer?.setMediaItem(MediaItem.fromUri(item.uri))
            exoPlayer?.prepare()
            exoPlayer?.play()
            EveState.updatePlayback(isPlaying = true, title = item.title)
            EveState.closeShelf()
            speakCleanText("جاري تشغيل ${item.title}")
        } else {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(item.uri, if (item.type == "VIDEO") "video/*" else "image/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            EveState.closeShelf()
        }
    }

    private fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                val current = EveState.uiState.value.playback
                EveState.updatePlayback(isPlaying = false, title = current.title)
            } else {
                player.play()
                val current = EveState.uiState.value.playback
                EveState.updatePlayback(isPlaying = true, title = current.title)
            }
        }
    }

    private fun skipNext() { speakCleanText("الأغنية التالية") }
    private fun skipPrev() { speakCleanText("الأغنية السابقة") }

    private fun setupScreenStateReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    stopListening()
                    EveState.setMood(EveMood.SLEEPING)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenReceiver, filter)
    }

    private fun setupBatteryGuardianReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                if (level >= 0 && scale > 0) {
                    val pct = (level * 100) / scale
                    if (isCharging) {
                        hasAlertedLowBattery = false
                        if (pct >= 85 && !hasAlertedFullCharge) {
                            hasAlertedFullCharge = true
                            speakCleanText("سيدي، وصل الشحن إلى $pct بالمائة، يمكنك فصل الشاحن للحفاظ على سلامة البطارية.")
                        }
                    } else {
                        hasAlertedFullCharge = false
                        if (pct <= 15 && !hasAlertedLowBattery) {
                            hasAlertedLowBattery = true
                            speakCleanText("تنبيه سيدي، طاقة البطارية منخفضة ووصلت إلى $pct بالمائة.")
                        }
                    }
                }
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    fun triggerDirectVoiceListening() {
        if (isHandlingAwake) return
        isHandlingAwake = true
        EveState.setMood(EveMood.AWAKE_SMILING)

        voiceInputHelper?.startListening(
            languageTag = "ar",
            onResult = { text ->
                isHandlingAwake = false
                processSpokenText(text)
            },
            onError = {
                isHandlingAwake = false
                // عند الصمت أو انتهاء الحوار، تعود للسبات بسلام
                EveState.setMood(EveMood.SLEEPING)
            }
        )
    }

    private fun processSpokenText(text: String) {
        serviceScope.launch(Dispatchers.Main) {
            val lower = text.lowercase().trim()

            // 1. أوامر إنهاء الحوار والسبات
            if (lower.contains("نامي") || lower.contains("سبات") || lower.contains("تصبحين على خير") || lower.contains("يكفي") || lower.contains("اغلقي")) {
                EveState.setMood(EveMood.SLEEPING)
                speakCleanText("في وضع السبات سيدي، أنا هنا متى احتجتني.", continueConversation = false)
                return@launch
            }

            // 2. القاموس الموسع لاستعراض وعرض الوسائط
            val isShow = lower.contains("استعرض") || lower.contains("استعرضي") || lower.contains("اعرض") ||
                         lower.contains("اعرضي") || lower.contains("افتح") || lower.contains("افتحي") ||
                         lower.contains("وريني") || lower.contains("أرني") || lower.contains("ارني") ||
                         lower.contains("شوفي") || lower.contains("شوف") || lower.contains("هاتي") || lower.contains("هات")

            if (isShow && (lower.contains("فيديو") || lower.contains("فيديوهات") || lower.contains("مقاطع") || lower.contains("مقطع"))) {
                EveState.openShelf(MediaShelfType.VIDEOS)
                speakCleanText("تفضل مقاطع الفيديو سيدي", continueConversation = true)
            } else if (isShow && (lower.contains("صور") || lower.contains("الصور") || lower.contains("صورة") || lower.contains("البوم") || lower.contains("ألبوم") || lower.contains("معرض"))) {
                EveState.openShelf(MediaShelfType.PHOTOS)
                speakCleanText("تفضل ألبوم الصور سيدي", continueConversation = true)
            } else if (isShow && (lower.contains("موسيقى") || lower.contains("اغاني") || lower.contains("أغاني") || lower.contains("صوتيات") || lower.contains("اناشيد"))) {
                EveState.openShelf(MediaShelfType.MUSIC)
                speakCleanText("تفضل قائمة الموسيقى سيدي", continueConversation = true)
            } else if (lower.contains("ماذا ترى") || lower.contains("شوفي الشاشة") || lower.contains("حللي الشاشة")) {
                handleScreenVisionQuery()
            } else {
                val cleanCommand = text.replace(Regex("(?i)eve|racha|ايف|إيف|ايڤ|اف|راشا|رشا|روشا|يا "), "").trim()

                if (cleanCommand.length > 2) {
                    val result = withContext(Dispatchers.IO) { intentHandler.handle(cleanCommand) }
                    when (result) {
                        is IntentHandler.HandlerResult.DirectReply -> {
                            speakCleanText(result.message, continueConversation = true)
                        }
                        is IntentHandler.HandlerResult.Error -> {
                            speakCleanText(result.message, continueConversation = true)
                        }
                        else -> {
                            val apiKey = securePreferences.getApiKey()
                            if (!apiKey.isNullOrBlank()) {
                                try {
                                    val aiReply = withContext(Dispatchers.IO) {
                                        GeminiService(apiKey).generateResponse(cleanCommand)
                                    }
                                    speakCleanText(aiReply, continueConversation = true)
                                } catch (e: Exception) {
                                    speakCleanText("أنا معك سيدي، تفضل بأمرك التالي.", continueConversation = true)
                                }
                            } else {
                                speakCleanText("أنا معك سيدي، تفضل بأمرك.", continueConversation = true)
                            }
                        }
                    }
                } else {
                    speakCleanText("أنا معك سيدي، تفضل بأمرك التالي.", continueConversation = true)
                }
            }
        }
    }

    private suspend fun handleScreenVisionQuery() {
        val snapshot = EveAccessibilityService.instance?.getLatestScreenSnapshot()
        if (snapshot == null || snapshot.visibleText.isBlank()) {
            speakCleanText("سيدي، لم أتمكن من استخراج محتوى الشاشة حالياً.", continueConversation = true)
            return
        }

        val apiKey = securePreferences.getApiKey()
        if (apiKey.isNullOrBlank()) {
            speakCleanText("أرى تطبيق ${snapshot.appName}: " + snapshot.visibleText.take(120), continueConversation = true)
            return
        }

        try {
            val aiAnalysis = withContext(Dispatchers.IO) {
                GeminiService(apiKey).analyzeScreenContext(snapshot.appName, snapshot.visibleText)
            }
            speakCleanText(aiAnalysis, continueConversation = true)
        } catch (e: Exception) {
            speakCleanText("أرى تطبيق ${snapshot.appName} سيدي، ولكن تعذر تحليل المحتوى.", continueConversation = true)
        }
    }

    private fun stopListening() {
        isHandlingAwake = false
        voiceInputHelper?.stopListening()
    }

    private fun updatePosition(dx: Float, dy: Float) {
        layoutParams.x += dx.toInt()
        layoutParams.y += dy.toInt()
        try { windowManager.updateViewLayout(composeView, layoutParams) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun handleEveClick() {
        triggerDirectVoiceListening()
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
            .setContentText("المساعد التكتيكي النشط")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        screenReceiver?.let { unregisterReceiver(it) }
        batteryReceiver?.let { unregisterReceiver(it) }
        exoPlayer?.release()
        voiceInputHelper?.destroy()
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
