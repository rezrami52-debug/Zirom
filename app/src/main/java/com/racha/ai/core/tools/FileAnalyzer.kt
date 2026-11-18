package com.racha.ai.core.tools

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object FileAnalyzer {

    suspend fun analyzeFile(context: Context, uri: Uri): FileAnalysisResult = withContext(Dispatchers.IO) {
        val fileName = getFileName(context, uri) ?: "ملف مجهول"
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val fileSize = getFileSize(context, uri)

        when (extension) {
            "apk" -> {
                FileAnalysisResult(
                    fileName = fileName,
                    fileSize = fileSize,
                    summary = "📱 ملف تطبيق أندرويد (APK): $fileName\nالحجم: $fileSize\n\nملاحظة: يمكنني مساعدتك في تثبيت هذا الملف أو استخراج أذوناته باستخدام Shizuku. هل تريد ذلك؟"
                )
            }
            "zip", "rar", "7z" -> {
                FileAnalysisResult(
                    fileName = fileName,
                    fileSize = fileSize,
                    summary = "📦 ملف مضغوط: $fileName\nالحجم: $fileSize\n\nملاحظة: لا يمكن قراءة محتوى الملفات المضغوطة مباشرة. يمكنني مساعدتك في فك ضغط هذا الملف باستخدام أوامر Shizuku إذا أردت."
                )
            }
            "txt", "json", "xml", "kt", "java", "py", "md", "csv" -> {
                val content = readTextFile(context, uri)
                FileAnalysisResult(
                    fileName = fileName,
                    fileSize = fileSize,
                    summary = "📄 ملف نصي: $fileName\nالحجم: $fileSize\n\nمقتطف من المحتوى:\n$content"
                )
            }
            else -> {
                FileAnalysisResult(
                    fileName = fileName,
                    fileSize = fileSize,
                    summary = "📎 ملف مرفق: $fileName\nالحجم: $fileSize\nالنوع: $extension\n\nملاحظة: هذا النوع من الملفات لا يدعم القراءة المباشرة، لكن تم حفظ مساره."
                )
            }
        }
    }

    private fun readTextFile(context: Context, uri: Uri): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            var line: String?
            var lineCount = 0
            while (reader.readLine().also { line = it } != null && lineCount < 50) {
                stringBuilder.append(line).append("\n")
                lineCount++
            }
            inputStream?.close()
            if (lineCount >= 50) stringBuilder.append("\n... (تم اقتطاع باقي الملف للاختصار)")
            stringBuilder.toString()
        } catch (e: Exception) {
            "تعذر قراءة محتوى الملف النصي."
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            result = result?.substring(result.lastIndexOf('/') + 1)
        }
        return result
    }

    private fun getFileSize(context: Context, uri: Uri): String {
        return try {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            val size = descriptor?.statSize ?: 0
            descriptor?.close()
            if (size > 1048576) "${size / 1048576} MB"
            else if (size > 1024) "${size / 1024} KB"
            else "$size Bytes"
        } catch (e: Exception) {
            "حجم غير معروف"
        }
    }
}

data class FileAnalysisResult(
    val fileName: String,
    val fileSize: String,
    val summary: String
)
