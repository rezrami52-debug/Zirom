package com.racha.ai.core.faceauth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * يلتقط صورة المتسلل بكاميرا أمامية بدون واجهة كاملة.
 * يستخدم CameraX + LifecycleOwner من MainActivity.
 *
 * ⚠️ ملاحظة: الكاميرا الأمامية قد تُظهر مؤشراً صغيراً على الشاشة (حسب الجهاز).
 */
object IntruderPhotoCapture {

    private const val TAG = "RachaIntruder"

    /**
     * محاولة التقاط صورة سريعة.
     *
     * @param context للصلاحيات
     * @param lifecycleOwner لربط CameraX
     * @param onComplete callback عند الانتهاء (نجاح أو فشل)
     */
    fun capture(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        onComplete: (File?) -> Unit
    ) {
        // تحقق من الصلاحية
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "صلاحية الكاميرا مفقودة")
            onComplete(null)
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageCapture
                )

                // التقاط الصورة
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = imageProxyToBitmap(image)
                            image.close()
                            cameraProvider.unbindAll()

                            if (bitmap != null) {
                                val file = saveIntruderPhoto(context, bitmap)
                                onComplete(file)
                            } else {
                                onComplete(null)
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "فشل التقاط صورة المتسلل", exception)
                            cameraProvider.unbindAll()
                            onComplete(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "فشل تهيئة الكاميرا", e)
                onComplete(null)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * حفظ صورة المتسلل في مجلد داخلي آمن.
     */
    private fun saveIntruderPhoto(context: Context, bitmap: Bitmap): File? {
        return try {
            val dir = File(context.filesDir, "intruders")
            if (!dir.exists()) dir.mkdirs()

            val filename = "intruder_${System.currentTimeMillis()}.jpg"
            val file = File(dir, filename)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            Log.d(TAG, "✅ تم حفظ صورة المتسلل: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "فشل حفظ الصورة", e)
            null
        }
    }

    /**
     * تحويل ImageProxy إلى Bitmap مع التدوير.
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val buffer: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

            val matrix = Matrix()
            matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())

            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
