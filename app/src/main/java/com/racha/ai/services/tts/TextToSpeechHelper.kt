package com.racha.ai.services.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * أداة قراءة النصوص بالعربية عبر TextToSpeech.
 * Singleton لأنه مكلف، ويُهيَّأ مرة واحدة.
 */
object TextToSpeechHelper {

    private const val TAG = "RachaTTS"
    private const val UTTERANCE_ID = "racha_notification"

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingText: String? = null

    /**
     * تهيئة TTS. يجب استدعاؤها مرة واحدة عند فتح التطبيق.
     */
    fun initialize(context: Context) {
        if (tts != null) return

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("ar"))
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    // العربية غير مثبتة، نجرب الافتراضية
                    Log.w(TAG, "اللغة العربية غير مدعومة - استخدام اللغة الافتراضية")
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.0f)

                isReady = true
                Log.d(TAG, "TTS جاهز")

                // إذا كان هناك نص في الانتظار
                pendingText?.let {
                    speak(it)
                    pendingText = null
                }
            } else {
                Log.e(TAG, "فشل تهيئة TTS - status=$status")
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "بدأ الكلام: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "انتهى الكلام: $utteranceId")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "خطأ في الكلام: $utteranceId")
            }
        })
    }

    /**
     * قراءة نص بصوت عالٍ.
     */
    fun speak(text: String) {
        if (text.isBlank()) return

        val engine = tts
        if (engine == null || !isReady) {
            pendingText = text
            return
        }

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /**
     * إيقاف القراءة الحالية.
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * تحرير الموارد. تُستدعى عند إغلاق التطبيق.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    /**
     * هل TTS جاهز؟
     */
    fun isInitialized(): Boolean = isReady
}
