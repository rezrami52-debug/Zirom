package com.racha.ai.core.eve

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.*
import android.graphics.PixelFormat
import android.os.Build
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
import com.racha.ai.core.media.MediaItemData
import com.racha.ai.core.overlay.OverlayLifecycleOwner
import com.racha.ai.core.voice.VoiceInputHelper
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

    // مشغل الموسيقى المدمج
    private var exoPlayer: ExoPlayer? = null

    // راصد تشغيل وإطفاء الشاشة للكلمة المفتاحية
    private var screenReceiver: BroadcastReceiver? = null
    private var isListeningWakeWord = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        voiceInputHelper = VoiceInputHelper(this)
        exoPlayer = ExoPlayer.Builder(this).build()

        startForegroundServiceNotification()
        setupHighQualityArabicTts()
        setupFloatingBubble()
        setupScreenStateReceiver()
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
                tts?.setSpeechRate(0.92f)
            }
        }
    }

    private fun speakCleanText(text: String) {
        val clean = text.replace(Regex("[\\p{So}\\p{Cn}\\*\\_\\#]"), "").trim()
        if (clean.isNotBlank()) tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "EVE_MSG")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingBubble() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // خاصية FLAG_WATCH_OUTSIDE_TOUCH تلتقط أي نقرة على شاشة الهاتف بعيداً عن EVE!
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

            // رصد النقر في أي مكان خارج EVE: تعود للسبات وتختفي أزرار التحكم مع استمرار الموسيقى
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
                            speakCleanText("نعم سيدي، في خدمتك")
                        } else {
                            speakCleanText("في وضع السبات سيدي")
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

    /**
     * عند النقر خارج EVE: تختفي الأزرار وتعود للسبات والموسيقى تعمل
     */
    private fun handleTapOutside() {
        EveState.closeShelf()
        EveState.setMood(EveMood.SLEEPING) // تغلق عينيها وتعود للسبات
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
            // فتح الفيديو أو الصورة عبر مشغل النظام
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

    private fun skipNext() {
        speakCleanText("الأغنية التالية")
    }

    private fun skipPrev() {
        speakCleanText("الأغنية السابقة")
    }

    /**
     * الاستماع لكلمة النداء (EVE / Racha) طالما شاشة الهاتف مضاءة
     */
    private fun setupScreenStateReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> startListeningForWakeWord()
                    Intent.ACTION_SCREEN_OFF -> stopListeningForWakeWord()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
        registerReceiver(screenReceiver, filter)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isInteractive) {
            startListeningForWakeWord()
        }
    }

    private fun startListeningForWakeWord() {
        if (isListeningWakeWord) return
        isListeningWakeWord = true

        voiceInputHelper?.startListening(
            languageTag = "ar",
            onResult = { text ->
                val lower = text.lowercase()
                // عند سماع EVE أو Racha تفتح عينيها فوراً
                if (lower.contains("eve") || lower.contains("ايف") || lower.contains("إيف") || lower.contains("racha") || lower.contains("راشا") || lower.contains("رشا")) {
                    EveState.setMood(EveMood.AWAKE_SMILING)
                    speakCleanText("أنا معك سيدي، ماذا تطلب؟")
                } else if (lower.contains("فيديو") || lower.contains("فيديوهات")) {
                    EveState.openShelf(MediaShelfType.VIDEOS)
                } else if (lower.contains("صور") || lower.contains("الصور")) {
                    EveState.openShelf(MediaShelfType.PHOTOS)
                } else if (lower.contains("موسيقى") || lower.contains("اغاني")) {
                    EveState.openShelf(MediaShelfType.MUSIC)
                }
                // إعادة حلقة الاستماع طالما الشاشة مضاءة
                isListeningWakeWord = false
                startListeningForWakeWord()
            },
            onError = {
                isListeningWakeWord = false
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (pm.isInteractive) startListeningForWakeWord()
            }
        )
    }

    private fun stopListeningForWakeWord() {
        isListeningWakeWord = false
        voiceInputHelper?.stopListening()
    }

    private fun updatePosition(dx: Float, dy: Float) {
        layoutParams.x += dx.toInt()
        layoutParams.y += dy.toInt()
        try { windowManager.updateViewLayout(composeView, layoutParams) } catch (e: Exception) { e.printStackTrace() }
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
            .setContentText("المساعد التكتيكي النشط")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        screenReceiver?.let { unregisterReceiver(it) }
        exoPlayer?.release()
        voiceInputHelper?.destroy()
        tts?.stop()
        tts?.shutdown()
        overlayLifecycleOwner.handleDestroy()
        composeView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
