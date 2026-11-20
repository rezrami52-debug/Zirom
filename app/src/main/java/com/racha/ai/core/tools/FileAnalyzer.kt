package com.racha.ai.core.tools

import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.OpenableColumns
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

/** تحليل ساكن محلي فقط: لا تثبيت، لا تشغيل للملف، ولا فكّ لمحتويات الأرشيف. */
object FileAnalyzer {
    private const val MAX_ARCHIVE_BYTES = 100L * 1024L * 1024L
    private const val MAX_TEXT_BYTES = 128 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 2_000
    private const val MAX_ARCHIVE_DECLARED_UNCOMPRESSED_BYTES = 512L * 1024L * 1024L
    private const val MAX_LISTED_ENTRIES = 35

    suspend fun analyzeFile(context: Context, uri: Uri): FileAnalysisResult = withContext(Dispatchers.IO) {
        val fileName = safeDisplayName(context, uri)
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val sizeText = formatSize(querySize(context, uri))

        try {
            when (extension) {
                "apk" -> analyzeApk(context, uri, fileName)
                "zip" -> analyzeZip(context, uri, fileName)
                "txt", "json", "xml", "kt", "java", "py", "md", "csv" ->
                    analyzeText(context, uri, fileName, sizeText)
                "rar", "7z" -> FileAnalysisResult(
                    fileName, sizeText,
                    "📦 الملف $fileName بصيغة .$extension. لا يتوفر محلل لهذه الصيغة في هذه النسخة؛ لم أفك الملف ولم أرسله إلى أي خدمة."
                )
                else -> FileAnalysisResult(
                    fileName, sizeText,
                    "📎 الملف: $fileName\nالحجم: $sizeText\nالنوع حسب الاسم: ${extension.ifBlank { "غير معروف" }}\n\nلم تُحلّل هذه الصيغة. لم أرفع الملف أو أشغّله."
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            FileAnalysisResult(
                fileName, sizeText,
                "تعذّر تحليل الملف «$fileName» محليًا: ${safeErrorMessage(error)}\nلم يتم تثبيته أو تشغيله أو رفعه."
            )
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun analyzeApk(context: Context, uri: Uri, fileName: String): FileAnalysisResult {
        val staged = stageFile(context, uri, MAX_ARCHIVE_BYTES)
        try {
            // PackageManager يقرأ بيانات الحزمة من الأرشيف ولا يثبّت التطبيق ولا يشغّله.
            val signingFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                signingFlag

            val packageInfo = context.packageManager.getPackageArchiveInfo(staged.file.absolutePath, flags)
                ?: throw IllegalArgumentException("Android لم يتعرّف إلى الملف على أنه APK صالح.")

            return FileAnalysisResult(
                fileName = fileName,
                fileSize = formatSize(staged.bytes),
                summary = buildApkSummary(packageInfo, fileName, staged.sha256)
            )
        } finally {
            staged.file.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun buildApkSummary(info: PackageInfo, fileName: String, fileSha256: String): String {
        val appInfo = info.applicationInfo
        val permissions = info.requestedPermissions.orEmpty().toSet()
        val reviewedPermissions = listOf(
            "android.permission.READ_SMS" to "قراءة الرسائل النصية",
            "android.permission.RECEIVE_SMS" to "استقبال الرسائل النصية",
            "android.permission.SEND_SMS" to "إرسال الرسائل النصية",
            "android.permission.READ_CALL_LOG" to "قراءة سجل المكالمات",
            "android.permission.WRITE_CALL_LOG" to "تعديل سجل المكالمات",
            "android.permission.READ_CONTACTS" to "قراءة جهات الاتصال",
            "android.permission.WRITE_CONTACTS" to "تعديل جهات الاتصال",
            "android.permission.CAMERA" to "الكاميرا",
            "android.permission.RECORD_AUDIO" to "الميكروفون",
            "android.permission.ACCESS_COARSE_LOCATION" to "الموقع التقريبي",
            "android.permission.ACCESS_FINE_LOCATION" to "الموقع الدقيق",
            "android.permission.ACCESS_BACKGROUND_LOCATION" to "الموقع في الخلفية",
            "android.permission.READ_PHONE_STATE" to "حالة الهاتف",
            "android.permission.SYSTEM_ALERT_WINDOW" to "الظهور فوق التطبيقات",
            "android.permission.REQUEST_INSTALL_PACKAGES" to "طلب تثبيت حزم أخرى"
        )
        val declaredReviewed = reviewedPermissions.filter { it.first in permissions }.map { it.second }

        val exportedComponents = buildList {
            info.activities.orEmpty().filter { it.exported }.forEach { add("نشاط: ${shortComponent(it)}") }
            info.services.orEmpty().filter { it.exported }.forEach { add("خدمة: ${shortComponent(it)}") }
            info.receivers.orEmpty().filter { it.exported }.forEach { add("مستقبِل: ${shortComponent(it)}") }
        }

        val appFlags = appInfo?.flags ?: 0
        val debugBuild = (appFlags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val backupAllowed = (appFlags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
        val cleartextAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            (appFlags and android.content.pm.ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0

        val signerDigest = signerSha256(info)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        return buildString {
            appendLine("📱 تحليل APK محلي: $fileName")
            appendLine("لم يتم تثبيت الملف أو تشغيله أو رفعه.")
            appendLine("الحزمة: ${info.packageName}")
            appendLine("الإصدار: ${info.versionName ?: "غير محدد"} (versionCode $versionCode)")
            appendLine("أذونات مطلوبة في البيان: ${permissions.size}")
            appendLine("أذونات مختارة للمراجعة: ${declaredReviewed.joinToString("، ").ifBlank { "لا توجد ضمن القائمة المختارة" }}")
            appendLine("مكوّنات مصدّرة: ${exportedComponents.size}")
            exportedComponents.take(15).forEach { appendLine("• $it") }
            if (exportedComponents.size > 15) appendLine("• عُرض أول 15 مكوّنًا فقط.")
            appendLine("قابل للتصحيح (debuggable): ${if (debugBuild) "نعم" else "لا"}")
            appendLine("يسمح بالنسخ الاحتياطي بحسب البيان: ${if (backupAllowed) "نعم" else "لا"}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                appendLine("السماح بحركة HTTP غير المشفرة بحسب الإعداد العام: ${if (cleartextAllowed) "نعم" else "لا"}")
            }
            appendLine("SHA-256 للملف: $fileSha256")
            appendLine("بصمة SHA-256 لشهادة التوقيع: ${signerDigest ?: "غير متاحة"}")
            appendLine("ملاحظة: البصمة ليست حكمًا على موثوقية الناشر، والأذونات والمكوّنات مؤشرات مراجعة وليست دليلًا على الضرر.")
            append("هذا تحليل ساكن محدود؛ لا يثبت أن التطبيق آمن أو خبيث.")
        }
    }

    @Suppress("DEPRECATION")
    private fun signerSha256(info: PackageInfo): String? {
        val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.mapNotNull { it.toByteArray() }
        } else {
            info.signatures?.mapNotNull { it.toByteArray() }
        }
        val certificate = certificates?.firstOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256").digest(certificate).toHex()
    }

    private fun shortComponent(component: ComponentInfo): String =
        component.name.substringAfterLast('.')

    private suspend fun analyzeZip(context: Context, uri: Uri, fileName: String): FileAnalysisResult {
        val staged = stageFile(context, uri, MAX_ARCHIVE_BYTES)
        try {
            val names = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            var entryCount = 0
            var fileCount = 0
            var declaredExpandedBytes = 0L

            ZipFile(staged.file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    coroutineContext.ensureActive()
                    if (entryCount >= MAX_ARCHIVE_ENTRIES) {
                        warnings += "توقف تعداد العناصر عند الحد الوقائي ($MAX_ARCHIVE_ENTRIES)."
                        break
                    }
                    val entry = entries.nextElement()
                    entryCount++
                    if (entry.isDirectory) continue
                    fileCount++

                    val entryName = entry.name
                    if (isUnsafeArchivePath(entryName)) {
                        warnings += "يوجد مسار داخل الأرشيف يبدو غير آمن (لم يُفك أي ملف)."
                    }
                    val size = entry.size
                    if (size > 0) {
                        declaredExpandedBytes = if (Long.MAX_VALUE - declaredExpandedBytes < size) {
                            Long.MAX_VALUE
                        } else {
                            declaredExpandedBytes + size
                        }
                    }
                    if (size > MAX_ARCHIVE_DECLARED_UNCOMPRESSED_BYTES) {
                        warnings += "أحد العناصر يعلن حجمًا كبيرًا جدًا بعد فك الضغط؛ لم يُفك المحتوى."
                    }
                    if (entry.compressedSize > 0 && size > 1_048_576L && size / entry.compressedSize > 100) {
                        warnings += "رُصد عنصر ذو نسبة ضغط معلنة مرتفعة؛ لم يُفك المحتوى."
                    }
                    if (isNestedArchive(entryName)) warnings += "يحتوي الأرشيف على أرشيف متداخل؛ لم يتم فتحه."
                    if (names.size < MAX_LISTED_ENTRIES) names += safeEntryName(entryName)
                }
            }

            if (declaredExpandedBytes > MAX_ARCHIVE_DECLARED_UNCOMPRESSED_BYTES) {
                warnings += "مجموع الأحجام غير المضغوطة المعلنة يتجاوز الحد الوقائي؛ لم تُفك الملفات."
            }

            return FileAnalysisResult(
                fileName = fileName,
                fileSize = formatSize(staged.bytes),
                summary = buildString {
                    appendLine("📦 فهرس ZIP محلي: $fileName")
                    appendLine("لم يتم فك أي محتوى أو تشغيله أو رفعه.")
                    appendLine("عدد العناصر المفحوصة: $entryCount (ملفات: $fileCount)")
                    appendLine("الحجم المضغوط: ${formatSize(staged.bytes)}؛ الحجم غير المضغوط المعلن: ${formatSize(declaredExpandedBytes)}")
                    appendLine("SHA-256 للملف: ${staged.sha256}")
                    appendLine("أسماء أول ${names.size} عنصرًا:")
                    if (names.isEmpty()) appendLine("• لا توجد ملفات قابلة للعرض.")
                    names.forEach { appendLine("• $it") }
                    if (fileCount > names.size) appendLine("• توجد عناصر إضافية لم تُعرض.")
                    if (warnings.isNotEmpty()) {
                        appendLine("ملاحظات تستحق الانتباه:")
                        warnings.distinct().take(8).forEach { appendLine("• $it") }
                    }
                    append("تنبيه: أسماء العناصر والبيانات الوصفية لا تثبت أن الملف ضار؛ لم يُحلل محتوى الملفات الداخلية.")
                }
            )
        } finally {
            staged.file.delete()
        }
    }

    private suspend fun analyzeText(context: Context, uri: Uri, fileName: String, sizeText: String): FileAnalysisResult {
        val bytes = ByteArrayOutputStream()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("تعذر فتح الملف المحدد.")
        var truncated = false
        input.use { source ->
            val buffer = ByteArray(8 * 1024)
            while (bytes.size() <= MAX_TEXT_BYTES) {
                coroutineContext.ensureActive()
                val remaining = MAX_TEXT_BYTES + 1 - bytes.size()
                if (remaining <= 0) { truncated = true; break }
                val read = source.read(buffer, 0, minOf(buffer.size, remaining))
                if (read < 0) break
                bytes.write(buffer, 0, read)
            }
            if (bytes.size() > MAX_TEXT_BYTES) truncated = true
        }
        val content = bytes.toByteArray().copyOf(minOf(bytes.size(), MAX_TEXT_BYTES)).toString(Charsets.UTF_8)
        return FileAnalysisResult(
            fileName, sizeText,
            buildString {
                appendLine("📄 مقتطف محلي من الملف النصي: $fileName")
                appendLine("الحجم: $sizeText؛ عُرض أول ${MAX_TEXT_BYTES / 1024} كيلوبايت كحد أقصى.")
                appendLine("لم يُرفع الملف تلقائيًا.")
                appendLine(content)
                if (truncated) appendLine("… اقتُطع باقي الملف بسبب حد القراءة.")
            }
        )
    }

    private data class StagedFile(val file: File, val bytes: Long, val sha256: String)

    private suspend fun stageFile(context: Context, uri: Uri, maxBytes: Long): StagedFile {
        val knownSize = querySize(context, uri)
        if (knownSize > maxBytes) throw IllegalArgumentException("حجم الملف يتجاوز الحد الوقائي ${formatSize(maxBytes)}.")
        val directory = File(context.cacheDir, "file_analysis")
        if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("تعذر إنشاء مساحة التحليل المؤقتة.")
        val temp = File(directory, "scan_${UUID.randomUUID()}.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("تعذر فتح الملف المحدد.")
            input.use { source ->
                FileOutputStream(temp).buffered().use { sink ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxBytes) throw IllegalArgumentException("تجاوز الملف الحد الوقائي ${formatSize(maxBytes)} أثناء القراءة.")
                        digest.update(buffer, 0, count)
                        sink.write(buffer, 0, count)
                    }
                }
            }
            if (total == 0L) throw IllegalArgumentException("الملف فارغ.")
            return StagedFile(temp, total, digest.digest().toHex())
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    private fun safeDisplayName(context: Context, uri: Uri): String {
        val raw = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "ملف مجهول"
        return raw.replace(Regex("[\\p{Cc}\\p{Cf}]"), " ").trim().take(120).ifBlank { "ملف مجهول" }
    }

    private fun querySize(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    }.getOrNull() ?: -1L

    private fun formatSize(bytes: Long): String = when {
        bytes < 0 -> "غير معروف"
        bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
        else -> "$bytes بايت"
    }

    private fun safeErrorMessage(error: Exception): String = when (error) {
        is SecurityException -> "لا يملك التطبيق إذن قراءة الملف المختار. أعد اختياره من منتقي الملفات."
        is java.util.zip.ZipException -> "بنية الأرشيف غير صالحة أو لا يمكن قراءتها."
        else -> error.message?.take(180) ?: "حدث خطأ غير متوقع."
    }

    private fun isUnsafeArchivePath(name: String): Boolean {
        val normalized = name.replace('\\', '/')
        return normalized.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(normalized) ||
            normalized.split('/').any { it == ".." }
    }

    private fun isNestedArchive(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("zip", "apk", "7z", "rar", "gz", "tar")

    private fun safeEntryName(name: String): String =
        name.replace(Regex("[\\p{Cc}\\p{Cf}]"), "?").take(180)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

data class FileAnalysisResult(
    val fileName: String,
    val fileSize: String,
    val summary: String
)
