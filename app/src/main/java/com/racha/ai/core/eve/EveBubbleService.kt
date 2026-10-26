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
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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
    private var lastMorningBriefingDate: String = ""

    private var isListeningWakeWord = false
    private var isHandlingAwake = false

    private var pendingNotificationJob: Job? = null
    private var currentNotifKey: String? = null

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
            }
        }
    }

    private fun speakCleanText(text: String) {
        val clean = text.replace(Regex("[\\p{So}\\p{Cn}\\*\\_\\#]"), "").trim()
        if (clean.isNotBlank()) tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "EVE_MSG")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRIGGER_NOTIFICATION_PULSE -> EveState.addNotification()
            ACTION_NEW_NOTIFICATION -> {
                val speechText = intent.getStringExtra(EXTRA_SPEECH_TEXT) ?: ""
                val notifKey = intent.getStringExtra(EXTRA_NOTIF_KEY)
                handleNewNotification(speechText, notifKey)
            }
            ACTION_CANCEL_NOTIFICATION -> {
                val notifKey = intent.getStringExtra(EXTRA_NOTIF_KEY)
                handleCancelNotification(notifKey)
            }
            ACTION_STOP_SERVICE -> stopSelf()
        }
        return START_STICKY
    }

    private fun handleNewNotification(speechText: String, notifKey: String?) {
        currentNotifKey = notifKey
        EveState.addNotification()

        pendingNotificationJob?.cancel()
        pendingNotificationJob = serviceScope.launch(Dispatchers.Main) {
            delay(15000)
            speakCleanText("سيدي، وصلك إشعار: $speechText")
        }
    }

    private fun handleCancelNotification(notifKey: String?) {
        if (notifKey == null || notifKey == currentNotifKey) {
            pendingNotificationJob?.cancel()
            pendingNotificationJob = null
            currentNotifKey = null
            EveState.clearNotifications()
        }
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
                            stopListeningForWakeWord()
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
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> checkAndTriggerMorningBriefing()
                    Intent.ACTION_SCREEN_OFF -> stopListeningForWakeWord()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
        registerReceiver(screenReceiver, filter)
    }

    private fun checkAndTriggerMorningBriefing() {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (hour in 6..10 && lastMorningBriefingDate != todayStr) {
            lastMorningBriefingDate = todayStr
            serviceScope.launch {
                delay(1200)
                speakCleanText("صباح الخير سيدي! أتمنى لك يوماً موفقاً وممتعاً. كل الأنظمة تعمل بكفاءة تامة.")
            }
        }
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
                            speakCleanText("سيدي، وصل الشحن إلى $pct بالمائة، يمكنك فصل الشاحن لحماية البطارية.")
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

    /**
     * استماع مباشر ونظيف عند لمس الفقاعة أو الأمر الصوتي دون أي حلقة مفرغة
     */
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
                EveState.setMood(EveMood.SLEEPING)
            }
        )
    }

    private fun processSpokenText(text: String) {
        serviceScope.launch(Dispatchers.Main) {
            val lower = text.lowercase()

            if (lower.contains("فيديو") || lower.contains("فيديوهات")) {
                EveState.openShelf(MediaShelfType.VIDEOS)
                speakCleanText("تفضل الفيديوهات سيدي")
            } else if (lower.contains("صور") || lower.contains("الصور")) {
                EveState.openShelf(MediaShelfType.PHOTOS)
                speakCleanText("تفضل ألبوم الصور")
            } else if (lower.contains("موسيقى") || lower.contains("اغاني")) {
                EveState.openShelf(MediaShelfType.MUSIC)
                speakCleanText("تفضل قائمة الموسيقى")
            } else if (lower.contains("ماذا ترى") || lower.contains("شوفي الشاشة") || lower.contains("حللي الشاشة")) {
                handleScreenVisionQuery()
            } else {
                val cleanCommand = text.replace(Regex("(?i)eve|racha|ايف|إيف|ايڤ|اف|راشا|رشا|روشا|يا "), "").trim()

                if (cleanCommand.length > 2) {
                    val result = withContext(Dispatchers.IO) { intentHandler.handle(cleanCommand) }
                    when (result) {
                        is IntentHandler.HandlerResult.DirectReply -> speakCleanText(result.message)
                        is IntentHandler.HandlerResult.Error -> speakCleanText(result.message)
                        else -> {
                            val apiKey = securePreferences.getApiKey()
                            if (!apiKey.isNullOrBlank()) {
                                try {
                                    val aiReply = withContext(Dispatchers.IO) {
                                        GeminiService(apiKey).generateResponse(cleanCommand)
                                    }
                                    speakCleanText(aiReply)
                                } catch (e: Exception) {
                                    speakCleanText("أنا معك سيدي، ماذا تطلب؟")
                                }
                            } else {
                                speakCleanText("أنا معك سيدي، ماذا تطلب؟")
                            }
                        }
                    }
                } else {
                    speakCleanText("أنا معك سيدي، ماذا تطلب؟")
                }
            }
        }
    }

    private suspend fun handleScreenVisionQuery() {
        val snapshot = EveAccessibilityService.instance?.getLatestScreenSnapshot()
        if (snapshot == null || snapshot.visibleText.isBlank()) {
            speakCleanText("سيدي، لم أتمكن من استخراج محتوى الشاشة حالياً.")
            return
        }

        val apiKey = securePreferences.getApiKey()
        if (apiKey.isNullOrBlank()) {
            speakCleanText("أرى تطبيق ${snapshot.appName}: " + snapshot.visibleText.take(120))
            return
        }

        try {
            val aiAnalysis = withContext(Dispatchers.IO) {
                GeminiService(apiKey).analyzeScreenContext(snapshot.appName, snapshot.visibleText)
            }
            speakCleanText(aiAnalysis)
        } catch (e: Exception) {
            speakCleanText("أرى تطبيق ${snapshot.appName} سيدي، ولكن تعذر تحليل المحتوى.")
        }
    }

    private fun stopListeningForWakeWord() {
        isListeningWakeWord = false
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
        pendingNotificationJob?.cancel()
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
        const val ACTION_NEW_NOTIFICATION = "ACTION_NEW_NOTIFICATION"
        const val ACTION_CANCEL_NOTIFICATION = "ACTION_CANCEL_NOTIFICATION"
        const val EXTRA_SPEECH_TEXT = "EXTRA_SPEECH_TEXT"
        const val EXTRA_NOTIF_KEY = "EXTRA_NOTIF_KEY"
    }
}
