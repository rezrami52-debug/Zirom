package com.racha.ai.core.voice

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class VoiceprintManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("racha_voiceprint_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SAVED_PITCH = "saved_owner_pitch"
        private const val KEY_IS_REGISTERED = "is_voice_registered"
        private const val SAMPLE_RATE = 16000
    }

    var isVoiceRegistered: Boolean
        get() = prefs.getBoolean(KEY_IS_REGISTERED, false)
        private set(value) = prefs.edit().putBoolean(KEY_IS_REGISTERED, value).apply()

    var savedPitch: Float
        get() = prefs.getFloat(KEY_SAVED_PITCH, 0f)
        private set(value) = prefs.edit().putFloat(KEY_SAVED_PITCH, value).apply()

    fun resetVoiceprint() {
        prefs.edit().clear().apply()
    }

    fun registerPitch(pitch: Float) {
        if (pitch > 60f && pitch < 400f) {
            savedPitch = pitch
            isVoiceRegistered = true
        }
    }

    /**
     * التحقق من مطابقة نبرة الصوت الحالية مع بصمة المالك المسجلة
     * نسبة التسامح 28% لتغطية تغير نبرة الصوت بين الصباح والمساء والإرهاق
     */
    fun isOwnerVoice(currentPitch: Float): Boolean {
        if (!isVoiceRegistered) {
            // إذا لم يتم تسجيل البصمة مسبقاً، نسجل هذا الصوت تلقائياً كصاحب الهاتف
            registerPitch(currentPitch)
            return true
        }
        if (currentPitch <= 0f) return true // في حال تعذر قراءة التردد بسبب تشويش مؤقت
        val diffRatio = abs(currentPitch - savedPitch) / savedPitch
        return diffRatio <= 0.28f
    }

    /**
     * استخراج التردد الأساسي (Fundamental Frequency F0) عبر خوارزمية التوافق الذاتي (Autocorrelation)
     */
    fun calculatePitchFromBuffer(audioData: ShortArray, readSize: Int): Float {
        if (readSize < 512) return 0f

        var maxCorr = 0.0
        var bestLag = -1

        val minLag = SAMPLE_RATE / 400 // الحد الأقصى للتردد البشري 400Hz
        val maxLag = SAMPLE_RATE / 70  // الحد الأدنى للتردد البشري 70Hz

        for (lag in minLag..maxLag) {
            var corr = 0.0
            for (i in 0 until (readSize - lag)) {
                corr += audioData[i] * audioData[i + lag]
            }
            if (corr > maxCorr) {
                maxCorr = corr
                bestLag = lag
            }
        }

        return if (bestLag > 0) {
            SAMPLE_RATE.toFloat() / bestLag.toFloat()
        } else {
            0f
        }
    }

    /**
     * تسجيل عينة سريعة (1 ثانية) للتحقق من تردد ونبرة المتحدث الحالية
     */
    suspend fun captureCurrentSpeakerPitch(): Float = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (bufferSize <= 0) return@withContext 0f

        var recorder: AudioRecord? = null
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            val audioBuffer = ShortArray(bufferSize)
            recorder.startRecording()
            val read = recorder.read(audioBuffer, 0, bufferSize)
            recorder.stop()

            if (read > 0) {
                return@withContext calculatePitchFromBuffer(audioBuffer, read)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                recorder?.release()
            } catch (e: Exception) { e.printStackTrace() }
        }
        return@withContext 0f
    }
}
