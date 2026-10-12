package com.racha.ai.core.faceauth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Repository لحفظ صورة الوجه (للتوثيق فقط) + حالة الحماية.
 */
class FaceAuthRepository(context: Context) {

    companion object {
        private const val TAG = "RachaFaceAuth"
        private const val FACE_FILE_NAME = "user_face.jpg"
        private const val PREF_KEY_ENABLED = "face_auth_enabled"
        private const val PREF_KEY_PHOTO_ENROLLED = "face_photo_enrolled"
    }

    private val internalDir = File(context.filesDir, "faceauth")
    private val faceFile = File(internalDir, FACE_FILE_NAME)

    private val prefs = context.getSharedPreferences("racha_face_auth", Context.MODE_PRIVATE)

    init {
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
    }

    fun saveFaceImage(bitmap: Bitmap): Boolean {
        return try {
            if (!internalDir.exists()) internalDir.mkdirs()
            FileOutputStream(faceFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            prefs.edit().putBoolean(PREF_KEY_PHOTO_ENROLLED, true).apply()
            Log.d(TAG, "✅ تم حفظ الصورة")
            true
        } catch (e: Exception) {
            Log.e(TAG, "فشل حفظ الصورة", e)
            false
        }
    }

    fun loadFaceImage(): Bitmap? {
        return try {
            if (!faceFile.exists()) return null
            BitmapFactory.decodeFile(faceFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "فشل تحميل الصورة", e)
            null
        }
    }

    fun deleteFaceImage() {
        try {
            if (faceFile.exists()) faceFile.delete()
            prefs.edit()
                .putBoolean(PREF_KEY_PHOTO_ENROLLED, false)
                .putBoolean(PREF_KEY_ENABLED, false)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "فشل الحذف", e)
        }
    }

    fun isPhotoEnrolled(): Boolean {
        return prefs.getBoolean(PREF_KEY_PHOTO_ENROLLED, false) && faceFile.exists()
    }

    fun isFaceAuthEnabled(): Boolean {
        return prefs.getBoolean(PREF_KEY_ENABLED, false)
    }

    fun setFaceAuthEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_ENABLED, enabled).apply()
    }

    /**
     * هل الجهاز يدعم البصمة/الوجه؟
     * يُستخدم في الإعدادات لعرض الحالة.
     */
    fun hasBiometricHardware(context: Context): Boolean {
        return try {
            val bm = androidx.biometric.BiometricManager.from(context)
            val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                 androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
            bm.canAuthenticate(authenticators) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }
}
