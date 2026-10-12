package com.racha.ai.core.faceauth

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * يستخرج "بصمة" رقمية (embedding) من صورة الوجه باستخدام MobileFaceNet.
 * الـ embedding = متجه 192 رقم يمثل ملامح الوجه.
 */
class FaceEmbeddingService(context: Context) {

    companion object {
        private const val TAG = "RachaFaceEmbedding"
        private const val MODEL_FILE = "mobile_face_net.tflite"
        private const val INPUT_SIZE = 112          // حجم الإدخال 112x112
        private const val EMBEDDING_SIZE = 192      // حجم المخرجات 192
    }

    private var interpreter: Interpreter? = null

    init {
        try {
            val model = loadModelFile(context)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(model, options)
            Log.d(TAG, "✅ تم تحميل النموذج بنجاح")
        } catch (e: Exception) {
            Log.e(TAG, "فشل تحميل النموذج", e)
        }
    }

    /**
     * تحميل ملف النموذج من assets.
     */
    private fun loadModelFile(context: Context): ByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * استخراج embedding من صورة.
     */
    fun extractEmbedding(bitmap: Bitmap): FloatArray? {
        val interp = interpreter ?: run {
            Log.e(TAG, "النموذج غير محمّل")
            return null
        }

        return try {
            // 1. تحويل الصورة إلى الحجم المناسب + Normalize
            val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

            // 2. تحويل إلى ByteBuffer (float32)
            val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
            inputBuffer.order(ByteOrder.nativeOrder())

            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            for (pixel in pixels) {
                val r = ((pixel shr 16) and 0xFF).toFloat()
                val g = ((pixel shr 8) and 0xFF).toFloat()
                val b = (pixel and 0xFF).toFloat()

                // Normalization: [0, 255] → [-1, 1]
                inputBuffer.putFloat((r - 127.5f) / 128.0f)
                inputBuffer.putFloat((g - 127.5f) / 128.0f)
                inputBuffer.putFloat((b - 127.5f) / 128.0f)
            }

            // 3. تجهيز مخرجات
            val outputArray = Array(1) { FloatArray(EMBEDDING_SIZE) }

            // 4. تشغيل النموذج
            interp.run(inputBuffer, outputArray)

            outputArray[0]
        } catch (e: Exception) {
            Log.e(TAG, "فشل استخراج embedding", e)
            null
        }
    }

    /**
     * حساب التشابه بين embedding-ين (Cosine Similarity).
     * النتيجة: بين -1 و 1. كلما اقترب من 1، كلما زاد التشابه.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        if (normA == 0f || normB == 0f) return 0f
        return dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    fun isLoaded(): Boolean = interpreter != null
}
