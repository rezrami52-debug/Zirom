package com.racha.ai.core.faceauth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

class FaceAuthRepository(context: Context) {

    companion object {
        private const val TAG = "RachaFaceAuth"
        private const val FACE_FILE_NAME = "user_face.jpg"
        private const val EMBEDDING_FILE_NAME = "user_face_embedding.bin"
        private const val PREF_KEY_ENABLED = "face_auth_enabled"
        private const val PREF_KEY_ENROLLED = "face_enrolled"
    }

    private val internalDir = File(context.filesDir, "faceauth")
    private val faceFile = File(internalDir, FACE_FILE_NAME)
    private val embeddingFile = File(internalDir, EMBEDDING_FILE_NAME)

    private val prefs = context.getSharedPreferences("racha_face_auth", Context.MODE_PRIVATE)

    init {
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
    }

    /**
     * حفظ صورة الوجه + embedding.
     */
    fun saveFaceImage(bitmap: Bitmap, embedding: FloatArray?): Boolean {
        return try {
            if (!internalDir.exists()) internalDir.mkdirs()

            // 1. حفظ الصورة
            FileOutputStream(faceFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            // 2. حفظ embedding إن وُجد
            if (embedding != null) {
                DataOutputStream(FileOutputStream(embeddingFile)).use { out ->
                    out.writeInt(embedding.size)
                    for (v in embedding) {
                        out.writeFloat(v)
                    }
                }
            }

            prefs.edit()
                .putBoolean(PREF_KEY_ENROLLED, true)
                .apply()

            Log.d(TAG, "✅ تم حفظ الصورة + embedding")
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

    /**
     * استرجاع الـ embedding المرجعي.
     */
    fun loadEmbedding(): FloatArray? {
        return try {
            if (!embeddingFile.exists()) return null
            DataInputStream(embeddingFile.inputStream()).use { input ->
                val size = input.readInt()
                val arr = FloatArray(size)
                for (i in 0 until size) {
                    arr[i] = input.readFloat()
                }
                arr
            }
        } catch (e: Exception) {
            Log.e(TAG, "فشل تحميل embedding", e)
            null
        }
    }

    fun deleteFaceImage() {
        try {
            if (faceFile.exists()) faceFile.delete()
            if (embeddingFile.exists()) embeddingFile.delete()
            prefs.edit()
                .putBoolean(PREF_KEY_ENROLLED, false)
                .putBoolean(PREF_KEY_ENABLED, false)
                .apply()
            Log.d(TAG, "تم حذف صورة الوجه + embedding")
        } catch (e: Exception) {
            Log.e(TAG, "فشل الحذف", e)
        }
    }

    fun isEnrolled(): Boolean {
        return prefs.getBoolean(PREF_KEY_ENROLLED, false) && faceFile.exists()
    }

    fun isFaceAuthEnabled(): Boolean {
        return prefs.getBoolean(PREF_KEY_ENABLED, false)
    }

    fun setFaceAuthEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_ENABLED, enabled).apply()
    }
}
