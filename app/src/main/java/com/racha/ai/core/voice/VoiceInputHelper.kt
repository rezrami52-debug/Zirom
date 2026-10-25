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
            // تجاهل في حال عدم توفر الصلاحية
        }
    }

    fun startListening(
        languageTag: String = "ar-TN",
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onPartialResult: (String) -> Unit = {},
        onReady: () -> Unit = {},
        onEnd: () -> Unit = {}
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("التعرف على الصوت غير متاح على هذا الجهاز")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "جاهز للاستماع")
                // إعادة الصوت بعد بدء الاستماع الصامت
                muteSystemAudio(false)
                onReady()
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "بدأ الكلام")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "انتهى الكلام")
                onEnd()
            }

            override fun onError(error: Int) {
                muteSystemAudio(false)
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "خطأ في الميكروفون"
                    SpeechRecognizer.ERROR_CLIENT -> "خطأ في التطبيق"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "صلاحية الميكروفون مفقودة"
                    SpeechRecognizer.ERROR_NETWORK -> "مشكلة في الشبكة"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "انتهت مهلة الشبكة"
                    SpeechRecognizer.ERROR_NO_MATCH -> "لم أسمع بوضوح"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "المستمع مشغول"
                    SpeechRecognizer.ERROR_SERVER -> "خطأ من الخادم"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "لم يُسمَع شيء"
                    else -> "خطأ غير معروف"
                }
                Log.e(TAG, "خطأ في التعرف: $message ($error)")
                onError(message)
            }

            override fun onResults(results: Bundle?) {
                muteSystemAudio(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                Log.d(TAG, "النتيجة: $text")
                if (text.isNotBlank()) {
                    onResult(text)
                } else {
                    onError("لم أسمع بوضوح")
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

        // كتم صوت الصفارة قبل الاستدعاء
        muteSystemAudio(true)
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        try {
            muteSystemAudio(false)
            recognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "فشل الإيقاف", e)
        }
    }

    fun destroy() {
        try {
            muteSystemAudio(false)
            recognizer?.destroy()
            recognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "فشل التدمير", e)
        }
    }
}
