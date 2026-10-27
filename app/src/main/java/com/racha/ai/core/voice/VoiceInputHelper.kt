package com.racha.ai.core.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceInputHelper(private val context: Context) {

    companion object {
        private const val TAG = "RachaVoice"
    }

    private var recognizer: SpeechRecognizer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isCurrentlyListening = false

    private fun muteSystemAudio(mute: Boolean) {
        try {
            val direction = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, direction, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, direction, 0)
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, mute)
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, mute)
            }
        } catch (_: Exception) {}
    }

    fun startListening(
        languageTag: String = "ar-TN",
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onPartialResult: (String) -> Unit = {},
        onReady: () -> Unit = {},
        onEnd: () -> Unit = {}
    ) {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("التعرف على الصوت غير متاح")
                return@post
            }

            // تنظيف أي استماع سابق معلق لمنع خطأ RECOGNIZER_BUSY
            try {
                recognizer?.cancel()
                recognizer?.destroy()
                recognizer = null
            } catch (_: Exception) {}

            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            } catch (e: Exception) {
                onError("فشل إنشاء المستمع: ${e.message}")
                return@post
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                
                // مهلة مريحة للمستخدم للبدء في الكلام دون إغلاق المايك سريعاً
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            }

            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isCurrentlyListening = true
                    muteSystemAudio(false)
                    onReady()
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isCurrentlyListening = false
                    muteSystemAudio(false)
                    onEnd()
                }

                override fun onError(error: Int) {
                    isCurrentlyListening = false
                    muteSystemAudio(false)
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "لم أسمع"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "مهلة صمت"
                        SpeechRecognizer.ERROR_AUDIO -> "خطأ مايكروفون"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "صلاحية المايك مفقودة"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "المستمع مشغول"
                        else -> "خطأ ($error)"
                    }
                    onError(msg)
                }

                override fun onResults(results: Bundle?) {
                    isCurrentlyListening = false
                    muteSystemAudio(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val bestText = matches?.firstOrNull { it.isNotBlank() } ?: ""
                    if (bestText.isNotBlank()) onResult(bestText) else onError("فارغ")
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) onPartialResult(text)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            try {
                muteSystemAudio(true)
                recognizer?.startListening(intent)
                // إعادة الصوت بعد 200ms كحماية في حال لم يطلق النظام onReadyForSpeech
                mainHandler.postDelayed({ muteSystemAudio(false) }, 250)
            } catch (e: Exception) {
                isCurrentlyListening = false
                muteSystemAudio(false)
                onError(e.message ?: "فشل البدء")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                isCurrentlyListening = false
                muteSystemAudio(false)
                recognizer?.stopListening()
                recognizer?.cancel()
            } catch (_: Exception) {}
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                isCurrentlyListening = false
                muteSystemAudio(false)
                recognizer?.destroy()
                recognizer = null
            } catch (_: Exception) {}
        }
    }
}
