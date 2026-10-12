package com.racha.ai.core.faceauth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Repository لحفظ واسترجاع صورة وجه المستخدم.
 * الصورة تُحفظ في مجلد داخلي خاص بالتطبيق (غير متاح لتطبيقات أخرى).
 */
class FaceAuthRepository(context: Context) {

    companion object {
        private const val TAG = "RachaFaceAuth"
        private const val FACE_FILE_NAME = "user_face.jpg"
        private const val PREF_KEY_ENABLED = "face_auth_enabled"
        private const val PREF_KEY_ENROLLED = "face_enrolled"
    }

    private val internalDir = File(context.filesDir, "faceauth")
    private val faceFile = File(internalDir, FACE_FILE_NAME)

    private val prefs = context.getSharedPreferences("racha_face_auth", Context.MODE_PRIVATE)

    init {
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
    }

    /**
     * حفظ صورة الوجه.
     */
    fun saveFaceImage(bitmap: Bitmap): Boolean {
        return try {
            if (!internalDir.exists()) internalDir.mkdirs()

            FileOutputStream(faceFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            prefs.edit()
                .putBoolean(PREF_KEY_ENROLLED, true)
                .apply()

            Log.d(TAG, "✅ تم حفظ صورة الوجه: ${faceFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "فشل حفظ الصورة", e)
            false
        }
    }

    /**
     * استرجاع صورة الوجه.
     */
    fun loadFaceImage(): Bitmap? {
        return try {
            if (!faceFile.exists()) return null
            BitmapFactory.decodeFile(faceFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "فشل تحميل الصورة", e)
            null
        }
    }

    /**
     * حذف صورة الوجه.
     */
    fun deleteFaceImage() {
        try {
            if (faceFile.exists()) faceFile.delete()
            prefs.edit()
                .putBoolean(PREF_KEY_ENROLLED, false)
                .putBoolean(PREF_KEY_ENABLED, false)
                .apply()
            Log.d(TAG, "تم حذف صورة الوجه")
        } catch (e: Exception) {
            Log.e(TAG, "فشل الحذف", e)
        }
    }

    /**
     * هل تم تسجيل الوجه؟
     */
    fun isEnrolled(): Boolean {
        return prefs.getBoolean(PREF_KEY_ENROLLED, false) && faceFile.exists()
    }

    /**
     * هل المصادقة مفعّلة؟
     */
    fun isFaceAuthEnabled(): Boolean {
        return prefs.getBoolean(PREF_KEY_ENABLED, false)
    }

    fun setFaceAuthEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_ENABLED, enabled).apply()
    }
}
