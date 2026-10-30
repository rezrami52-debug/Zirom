package com.racha.ai.core.eve

import android.animation.ValueAnimator
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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.racha.ai.core.apps.AppResolver
import com.racha.ai.core.apps.IntentHandler
import com.racha.ai.core.contacts.ContactResolver
import com.racha.ai.core.media.MediaItemData
import com.racha.ai.core.overlay.OverlayLifecycleOwner
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.core.voice.VoiceInputHelper
import com.racha.ai.data.api.GeminiService
import com.racha.ai.data.llm.QwenService
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class EveBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private val overlayLifecycleOwner = OverlayLifecycleOwner()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // 1. فقاعة EVE
    private var bubbleView: ComposeView? = null
    private lateinit var bubbleLayoutParams: WindowManager.LayoutParams

    // 2. خريطة النوافذ العائمة المتعددة المستقلة تماماً (True Multi-Window)
    private data class ShelfWindowHolder(
        val type: MediaShelfType,
        val view: ComposeView,
        val layoutParams: WindowManager.LayoutParams
    )
    private val activeShelfWindows = mutableMapOf<MediaShelfType, ShelfWindowHolder>()

    private var tts: TextToSpeech? = null
    private var voiceInputHelper: VoiceInputHelper? = null
    private lateinit var intentHandler: IntentHandler
    private lateinit var securePreferences: SecurePreferences
    private lateinit var appPreferences: AppPreferences
    private lateinit var qwenService: QwenService

    private var exoPlayer: ExoPlayer? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var currentBatteryPct = 100
    private var isPhoneCharging = false
    private var hasAlertedFullCharge = false
    private var hasAlertedLowBattery = false

    private var isHandlingAwake = false
    private var pendingNotificationJob: Job? = null
    private var currentNotifKey: String? = null

    private val conversationHistory = mutableListOf<Pair<String, String>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        voiceInputHelper = VoiceInputHelper(this)
        securePreferences = SecurePreferences(this)
        appPreferences = AppPreferences(this)
        intentHandler = IntentHandler(this, AppResolver(this), ContactResolver(this))
        qwenService = QwenService(this)
        exoPlayer = ExoPlayer.Builder(this).build()

        startForegroundServiceNotification()
        setupHighQualityArabicTts()
        setupFloatingBubble()
        setupMultiWindowObserver()
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
                } catch (_: Exception) {}
                tts?.setPitch(appPreferences.getTtsPitch())
                tts?.setSpeechRate(appPreferences.getTtsSpeed())

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "CONTINUE_CONVERSATION" && appPreferences.isContinuousChatEnabled()) {
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
            ACTION_NEW_NOTIFICATION -> {
                val speechText = intent.getStringExtra(EXTRA_SPEECH_TEXT) ?: ""
                val notifKey = intent.getStringExtra(EXTRA_NOTIF_KEY)
                handleNewNotification(speechText, notifKey)
            }
            ACTION_CANCEL_NOTIFICATION -> {
                val notifKey = intent.getStringExtra(EXTRA_NOTIF_KEY)
                handleCancelNotification(notifKey)
            }
            ACTION_STOP_SERVICE -> closeAndDismiss()
        }
        return START_STICKY
    }

    private fun handleNewNotification(speechText: String, notifKey: String?) {
        currentNotifKey = notifKey
        EveState.addNotification()
        pendingNotificationJob?.cancel()
        pendingNotificationJob = serviceScope.launch(Dispatchers.Main) {
            delay(15000)
            speakCleanText("رمزي، وصلك إشعار: $speechText", continueConversation = false)
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

        bubbleLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 280
        }

        bubbleView = ComposeView(this).apply {
            overlayLifecycleOwner.attachToComposeView(this)
            setContent {
                EveBubbleAvatar(
                    onToggleSleepWake = { isAwake ->
                        if (isAwake) {
                            if (tts?.isSpeaking == true) tts?.stop()
                            triggerDirectVoiceListening()
                        } else {
                            speakCleanText("في وضع السبات، أنا هنا متى احتجتني.")
                            stopListening()
                        }
                    },
                    onOpenApp = { handleEveClick() },
                    onDismiss = { closeAndDismiss() },
                    onMediaPlayPause = { togglePlayPause() },
                    onMediaNext = { skipNext() },
                    onMediaPrev = { skipPrev() },
                    onDrag = { dx, dy -> updateBubblePosition(dx, dy) },
                    onDragEnd = { handleDragEnd() }
                )
            }
        }

        try {
            windowManager.addView(bubbleView, bubbleLayoutParams)
            overlayLifecycleOwner.handleResume()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun setupMultiWindowObserver() {
        serviceScope.launch {
            EveState.uiState.collect { state ->
                val desiredShelves = state.openShelves

                val toRemove = activeShelfWindows.keys.filter { it !in desiredShelves }
                for (type in toRemove) {
                    activeShelfWindows[type]?.let { holder ->
                        try {
                            windowManager.removeView(holder.view)
                        } catch (_: Exception) {}
                    }
                    activeShelfWindows.remove(type)
                }

                var cascadeOffset = 0
                for (type in desiredShelves) {
                    if (type !in activeShelfWindows) {
                        createAndAddShelfWindow(type, cascadeOffset)
                        cascadeOffset += 50
                    }
                }
            }
        }
    }

    private fun createAndAddShelfWindow(type: MediaShelfType, offset: Int) {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val defaultWidth = (280 * density).toInt()
        val defaultHeight = (380 * density).toInt()

        val lp = WindowManager.LayoutParams(
            defaultWidth,
            defaultHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (70 * density).toInt() + offset
            y = (180 * density).toInt() + offset
        }

        val view = ComposeView(this).apply {
            overlayLifecycleOwner.attachToComposeView(this)
            setContent {
                EveStandaloneShelfWindow(
                    targetShelfType = type,
                    onClose = { EveState.closeShelf(type) },
                    onMoveWindow = { dx, dy -> updateShelfPosition(type, dx, dy) },
                    onResizeWindow = { dw, dh -> resizeShelfWindow(type, dw, dh) },
                    onBringToFront = { bringWindowToFront(type) },
                    onPlayAudio = { item -> playSelectedAudio(item) }
                )
            }
        }

        try {
            windowManager.addView(view, lp)
            activeShelfWindows[type] = ShelfWindowHolder(type, view, lp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun bringWindowToFront(type: MediaShelfType) {
        activeShelfWindows[type]?.let { holder ->
            try {
                windowManager.removeView(holder.view)
                windowManager.addView(holder.view, holder.layoutParams)
            } catch (_: Exception) {}
        }
    }

    private fun updateShelfPosition(type: MediaShelfType, dx: Float, dy: Float) {
        activeShelfWindows[type]?.let { holder ->
            holder.layoutParams.x += dx.roundToInt()
            holder.layoutParams.y += dy.roundToInt()
            try {
                windowManager.updateViewLayout(holder.view, holder.layoutParams)
            } catch (_: Exception) {}
        }
    }

    private fun resizeShelfWindow(type: MediaShelfType, dw: Float, dh: Float) {
        activeShelfWindows[type]?.let { holder ->
            val dm = resources.displayMetrics
            val minW = (220 * dm.density).toInt()
            val minH = (260 * dm.density).toInt()
            val maxW = dm.widthPixels - 20
            val maxH = dm.heightPixels - 120

            holder.layoutParams.width = (holder.layoutParams.width + dw.roundToInt()).coerceIn(minW, maxW)
            holder.layoutParams.height = (holder.layoutParams.height + dh.roundToInt()).coerceIn(minH, maxH)
            try {
                windowManager.updateViewLayout(holder.view, holder.layoutParams)
            } catch (_: Exception) {}
        }
    }

    private fun handleDragEnd() {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels

        if (bubbleLayoutParams.y > screenHeight * 0.50f) {
            closeAndDismiss()
        } else {
            if (appPreferences.isEdgeSnapEnabled()) {
                snapToNearestEdge()
            }
        }
    }

    private fun closeAndDismiss() {
        for (holder in activeShelfWindows.values) {
            try { windowManager.removeView(holder.view) } catch (_: Exception) {}
        }
        activeShelfWindows.clear()
        EveState.closeAllShelves()

        try {
            bubbleView?.let { windowManager.removeView(it) }
            bubbleView = null
        } catch (_: Exception) {}
        stopSelf()
    }

    private fun snapToNearestEdge() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val bubbleWidth = bubbleView?.width ?: 200

        val targetX = if (bubbleLayoutParams.x + bubbleWidth / 2 < screenWidth / 2) 20 else screenWidth - bubbleWidth - 20
        bubbleLayoutParams.y = bubbleLayoutParams.y.coerceIn(80, screenHeight - 340)

        ValueAnimator.ofInt(bubbleLayoutParams.x, targetX).apply {
            duration = 240
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                bubbleLayoutParams.x = animation.animatedValue as Int
                try { windowManager.updateViewLayout(bubbleView, bubbleLayoutParams) } catch (_: Exception) {}
            }
        }.start()
    }

    private fun playSelectedAudio(item: MediaItemData) {
        if (item.type == "AUDIO") {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            exoPlayer?.setMediaItem(MediaItem.fromUri(item.uri))
            exoPlayer?.prepare()
            exoPlayer?.play()
            EveState.updatePlayback(isPlaying = true, title = item.title)
            speakCleanText("جاري تشغيل ${item.title}")
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
                    conversationHistory.clear()
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
                isPhoneCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                if (level >= 0 && scale > 0) {
                    currentBatteryPct = (level * 100) / scale
                    if (isPhoneCharging) {
                        hasAlertedLowBattery = false
                        if (currentBatteryPct >= 85 && !hasAlertedFullCharge) {
                            hasAlertedFullCharge = true
                            speakCleanText("رمزي، وصل الشحن إلى $currentBatteryPct بالمائة، يمكنك فصل الشاحن لحماية البطارية.")
                        }
                    } else {
                        hasAlertedFullCharge = false
                        if (currentBatteryPct <= 15 && !hasAlertedLowBattery) {
                            hasAlertedLowBattery = true
                            speakCleanText("تنبيه، طاقة البطارية منخفضة ووصلت إلى $currentBatteryPct بالمائة.")
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
            onError = { errorMsg ->
                isHandlingAwake = false
                if (errorMsg.contains("لم أسمع") || errorMsg.contains("مهلة صمت")) {
                    speakCleanText("لم أسمعك بوضوح يا رمزي، تفضل بإعادة طلبك.", continueConversation = false)
                    EveState.setMood(EveMood.SLEEPING)
                } else {
                    EveState.setMood(EveMood.SLEEPING)
                }
            }
        )
    }

    private fun getLiveDeviceContext(): String {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val chargingStr = if (isPhoneCharging) "قيد الشحن" else "يعمل على البطارية"
        val activeApp = EveAccessibilityService.instance?.getLatestScreenSnapshot()?.appName ?: "الشاشة الرئيسية"
        return "الوقت الآن: $time | البطارية: $currentBatteryPct% ($chargingStr) | التطبيق المفتوح: $activeApp | المستخدم: رمزي | الموقع: سوسة، تونس."
    }

    private fun processSpokenText(text: String) {
        serviceScope.launch(Dispatchers.Main) {
            val lower = text.lowercase().trim()

            if (lower.contains("نامي") || lower.contains("سبات") || lower.contains("تصبحين على خير") || lower.contains("يكفي") || lower.contains("اغلقي")) {
                conversationHistory.clear()
                EveState.setMood(EveMood.SLEEPING)
                speakCleanText("في وضع السبات يا رمزي، أنا هنا متى احتجتني.", continueConversation = false)
                return@launch
            }

            val isShow = lower.contains("استعرض") || lower.contains("استعرضي") || lower.contains("اعرض") ||
                         lower.contains("اعرضي") || lower.contains("افتح") || lower.contains("افتحي") ||
                         lower.contains("وريني") || lower.contains("أرني") || lower.contains("ارني") ||
                         lower.contains("شوفي") || lower.contains("شوف") || lower.contains("هاتي") ||
                         lower.contains("هات") || lower.contains("حل") || lower.contains("حليلي") ||
                         lower.contains("فرجني") || lower.contains("فرّجني") || lower.contains("خرجلي")

            val hasVideo = lower.contains("فيديو") || lower.contains("فيديوهات") || lower.contains("مقاطع")
            val hasPhoto = lower.contains("صور") || lower.contains("صورة") || lower.contains("معرض") || lower.contains("البوم")
            val hasMusic = lower.contains("موسيقى") || lower.contains("اغاني") || lower.contains("أغاني") || lower.contains("صوتيات")
            val hasNotif = lower.contains("اشعار") || lower.contains("اشعارات") || lower.contains("إشعار")

            val mediaCount = listOf(hasVideo, hasPhoto, hasMusic, hasNotif).count { it }
            val isOtherWindow = lower.contains("اخرى") || lower.contains("أخرى") || lower.contains("منفصلة") ||
                                lower.contains("كل") || lower.contains("مع بعض") || lower.contains("نوافذ")

            if (isShow && (mediaCount >= 2 || (mediaCount >= 1 && isOtherWindow))) {
                val types = mutableListOf<MediaShelfType>()
                if (hasVideo) types.add(MediaShelfType.VIDEOS)
                if (hasPhoto) types.add(MediaShelfType.PHOTOS)
                if (hasMusic) types.add(MediaShelfType.MUSIC)
                if (hasNotif) types.add(MediaShelfType.NOTIFICATIONS)
                if (types.isEmpty()) {
                    if (hasPhoto) types.add(MediaShelfType.PHOTOS)
                    else if (hasVideo) types.add(MediaShelfType.VIDEOS)
                    else if (hasMusic) types.add(MediaShelfType.MUSIC)
                }
                EveState.openMultipleShelves(types)
                speakCleanText("من عيوني يا رمزي، فتحت لك النوافذ معاً على الشاشة.", continueConversation = false)
            } else if (isShow && hasNotif) {
                EveState.openShelf(MediaShelfType.NOTIFICATIONS)
                speakCleanText("تفضل الإشعارات الواردة يا رمزي", continueConversation = false)
            } else if (isShow && hasVideo) {
                EveState.openShelf(MediaShelfType.VIDEOS)
                speakCleanText("تفضل مقاطع الفيديو يا رمزي", continueConversation = false)
            } else if (isShow && hasPhoto) {
                EveState.openShelf(MediaShelfType.PHOTOS)
                speakCleanText("تفضل ألبوم الصور", continueConversation = false)
            } else if (isShow && hasMusic) {
                EveState.openShelf(MediaShelfType.MUSIC)
                speakCleanText("تفضل قائمة الموسيقى", continueConversation = false)
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
                            val selectedProvider = appPreferences.getLLMProvider()

                            if (selectedProvider == "qwen" && qwenService.isModelPresent()) {
                                try {
                                    val qwenReply = withContext(Dispatchers.IO) { qwenService.generateResponse(cleanCommand) }
                                    speakCleanText(qwenReply, continueConversation = true)
                                    return@launch
                                } catch (_: Exception) {}
                            }

                            val apiKey = securePreferences.getApiKey()
                            if (!apiKey.isNullOrBlank()) {
                                try {
                                    val liveContext = getLiveDeviceContext()
                                    val contextualPrompt = "[$liveContext]\nسؤال رمزي: $cleanCommand"

                                    val aiReply = withContext(Dispatchers.IO) {
                                        GeminiService(apiKey).generateResponse(contextualPrompt)
                                    }

                                    conversationHistory.add(Pair("user", cleanCommand))
                                    conversationHistory.add(Pair("assistant", aiReply))
                                    if (conversationHistory.size > 8) {
                                        conversationHistory.removeAt(0)
                                        conversationHistory.removeAt(0)
                                    }

                                    speakCleanText(aiReply, continueConversation = true)
                                } catch (e: Exception) {
                                    if (qwenService.isModelPresent()) {
                                        val fallback = withContext(Dispatchers.IO) { qwenService.generateResponse(cleanCommand) }
                                        speakCleanText(fallback, continueConversation = true)
                                    } else {
                                        speakCleanText("أنا معك يا رمزي، تفضل بأمرك.", continueConversation = true)
                                    }
                                }
                            } else {
                                if (qwenService.isModelPresent()) {
                                    val fallback = withContext(Dispatchers.IO) { qwenService.generateResponse(cleanCommand) }
                                    speakCleanText(fallback, continueConversation = true)
                                } else {
                                    speakCleanText("أنا معك يا رمزي، تفضل بأمرك.", continueConversation = true)
                                }
                            }
                        }
                    }
                } else {
                    speakCleanText("أنا معك يا رمزي، تفضل بأمرك التالي.", continueConversation = true)
                }
            }
        }
    }

    private suspend fun handleScreenVisionQuery() {
        val snapshot = EveAccessibilityService.instance?.getLatestScreenSnapshot()
        if (snapshot == null || snapshot.visibleText.isBlank()) {
            speakCleanText("لم أتمكن من استخراج محتوى الشاشة حالياً يا رمزي.", continueConversation = true)
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
        } catch (_: Exception) {
            speakCleanText("أرى تطبيق ${snapshot.appName}، ولكن تعذر تحليل المحتوى.", continueConversation = true)
        }
    }

    private fun stopListening() {
        isHandlingAwake = false
        voiceInputHelper?.stopListening()
    }

    private fun updateBubblePosition(dx: Float, dy: Float) {
        bubbleLayoutParams.x += dx.roundToInt()
        bubbleLayoutParams.y += dy.roundToInt()
        try { windowManager.updateViewLayout(bubbleView, bubbleLayoutParams) } catch (_: Exception) {}
    }

    private fun handleEveClick() {
        if (tts?.isSpeaking == true) tts?.stop()
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
        for (holder in activeShelfWindows.values) {
            try { windowManager.removeView(holder.view) } catch (_: Exception) {}
        }
        activeShelfWindows.clear()
        screenReceiver?.let { unregisterReceiver(it) }
        batteryReceiver?.let { unregisterReceiver(it) }
        exoPlayer?.release()
        voiceInputHelper?.destroy()
        tts?.stop()
        tts?.shutdown()
        overlayLifecycleOwner.handleDestroy()
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            bubbleView = null
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
