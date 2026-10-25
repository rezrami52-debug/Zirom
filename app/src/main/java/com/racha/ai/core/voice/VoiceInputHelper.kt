package com.racha.ai.core.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
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
    private var isCurrentlyListening = false

    private fun muteSystemAudio(mute: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val direction = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, direction, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, direction, 0)
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, mute)
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, mute)
            }
        } catch (e: Exception) {
            // تجاهل إن كانت الصلاحية غير متوفرة
        }
    }

    fun startListening(
        languageTag: String = "ar",
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onPartialResult: (String) -> Unit = {},
        onReady: () -> Unit = {},
        onEnd: () -> Unit = {}
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("التعرف على الصوت غير متاح")
            return
        }

        if (isCurrentlyListening) {
            return
        }

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra("android.speech.extra.DICTATION_MODE", true)
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
                onEnd()
            }

            override fun onError(error: Int) {
                isCurrentlyListening = false
                muteSystemAudio(false)
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "خطأ ميكروفون"
                    SpeechRecognizer.ERROR_NO_MATCH -> "لم أسمع"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "انتهت المهلة"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "المستمع مشغول"
                    else -> "خطأ ($error)"
                }
                onError(message)
            }

            override fun onResults(results: Bundle?) {
                isCurrentlyListening = false
                muteSystemAudio(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    onResult(text)
                } else {
                    onError("فارغ")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    onPartialResult(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            muteSystemAudio(true)
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            isCurrentlyListening = false
            onError(e.message ?: "فشل البدء")
        }
    }

    fun stopListening() {
        try {
            isCurrentlyListening = false
            muteSystemAudio(false)
            recognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "فشل الإيقاف", e)
        }
    }

    fun destroy() {
        try {
            isCurrentlyListening = false
            muteSystemAudio(false)
            recognizer?.destroy()
            recognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "فشل التدمير", e)
        }
    }
}
