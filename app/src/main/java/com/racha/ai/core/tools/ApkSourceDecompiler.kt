package com.racha.ai.core.tools

import android.content.Context
import android.net.Uri
import android.os.Build
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.impl.NoOpCodeCache
import jadx.api.impl.SimpleCodeWriter
import jadx.api.security.JadxSecurityFlag
import jadx.api.security.impl.JadxSecurity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.function.Function
import java.util.function.Predicate
import kotlin.coroutines.coroutineContext
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Experimental local APK source decompilation.
 * It emits Java-like code for the package declared by the APK, not third-party packages.
 * It never installs, launches, or uploads the selected APK.
 */
object ApkSourceDecompiler {
    private const val MAX_APK_BYTES = 160L * 1024L * 1024L
    private const val MAX_OUTPUT_BYTES = 100L * 1024L * 1024L
    private const val COPY_BUFFER_BYTES = 32 * 1024

    data class Result(
        val archive: File,
        val packageName: String,
        val javaFileCount: Int,
        val jadxErrors: Int,
        val archiveBytes: Long
    )

    suspend fun decompileMainPackage(
        context: Context,
        uri: Uri,
        onProgress: (done: Long, total: Long) -> Unit,
        shouldCancel: () -> Boolean
    ): Result = withContext(Dispatchers.IO) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            "تفكيك الشيفرة التجريبي يتطلب Android 9 أو أحدث (API 28)."
        }

        val workRoot = File(context.cacheDir, "racha_jadx").apply {
            if (!exists() && !mkdirs()) error("تعذر إنشاء مجلد العمل المؤقت.")
        }
        val jobId = UUID.randomUUID().toString()
        val stagedApk = File(workRoot, "$jobId.apk")
        val outputDir = File(workRoot, jobId)
        val archive = File(workRoot, "$jobId-sources.zip")

        try {
            copySelectedApk(context, uri, stagedApk)
            currentCoroutineContext().ensureActive()
            if (shouldCancel()) throw CancellationException("تم إلغاء التفكيك.")

            val packageName = readPackageName(context, stagedApk)
            val args = JadxArgs().apply {
                inputFiles = listOf(stagedApk)
                outDir = outputDir
                threadsCount = 1
                codeCache = NoOpCodeCache.INSTANCE
                codeWriterProvider = Function(::SimpleCodeWriter)
                classFilter = Predicate { className ->
                    className == packageName || className.startsWith("$packageName.")
                }
                setIncludeDependencies(false)
                isLoadJadxClsSetFile = false

                // The official Android sample disables this parser option because it may be
                // unsupported on Android. Other security checks remain enabled.
                val flags = JadxSecurityFlag.all()
                flags.remove(JadxSecurityFlag.SECURE_XML_PARSER)
                security = JadxSecurity(flags)
            }

            var jadxErrors = 0
            JadxDecompiler(args).use { jadx ->
                jadx.load()
                if (shouldCancel()) throw CancellationException("تم إلغاء التفكيك.")
                saveWithProgress(jadx, onProgress, shouldCancel)
                jadxErrors = jadx.errorsCount
            }

            currentCoroutineContext().ensureActive()
            if (shouldCancel()) throw CancellationException("تم إلغاء التفكيك.")

            val sourceRoot = File(outputDir, "sources")
            val javaFiles = sourceRoot.walkTopDown()
                .filter { it.isFile && it.extension.equals("java", ignoreCase = true) }
                .toList()
            val expectedPackageDir = File(sourceRoot, packageName.replace('.', File.separatorChar))
            val packageJavaFiles = if (expectedPackageDir.exists()) {
                expectedPackageDir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("java", ignoreCase = true) }
                    .toList()
            } else {
                javaFiles.filter { file ->
                    file.relativeTo(sourceRoot).invariantSeparatorsPath
                        .startsWith(packageName.replace('.', '/') + "/")
                }
            }
            require(packageJavaFiles.isNotEmpty()) {
                "JADX لم ينتج ملفات Java-like للحزمة $packageName. راجع سجل الأخطاء أو جرّب APK آخر."
            }

            var expandedBytes = 0L
            val resourceRoot = File(outputDir, "resources")
            val filesToArchive = (packageJavaFiles + resourceRoot.walkTopDown().filter { it.isFile }.toList())
                .distinctBy { it.absolutePath }
            filesToArchive.forEach { file ->
                currentCoroutineContext().ensureActive()
                expandedBytes += file.length()
                require(expandedBytes <= MAX_OUTPUT_BYTES) {
                    "تجاوز ناتج الشيفرة والموارد الحد الوقائي البالغ 100 MB. لم يُصدّر الملف."
                }
            }

            FileOutputStream(archive).use { fileOutput ->
                ZipOutputStream(fileOutput.buffered()).use { zip ->
                    filesToArchive.forEach { file ->
                        currentCoroutineContext().ensureActive()
                        if (shouldCancel()) throw CancellationException("تم إلغاء التصدير.")
                        val relative = if (file.absolutePath.startsWith(sourceRoot.absolutePath + File.separator)) {
                            "sources/" + file.relativeTo(sourceRoot).invariantSeparatorsPath
                        } else {
                            "resources/" + file.relativeTo(resourceRoot).invariantSeparatorsPath
                        }
                        zip.putNextEntry(ZipEntry(relative))
                        file.inputStream().buffered().use { input ->
                            val buffer = ByteArray(COPY_BUFFER_BYTES)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                zip.write(buffer, 0, count)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }

            Result(
                archive = archive,
                packageName = packageName,
                javaFileCount = packageJavaFiles.size,
                jadxErrors = jadxErrors,
                archiveBytes = archive.length()
            )
        } catch (cancelled: CancellationException) {
            archive.delete()
            throw cancelled
        } catch (error: Throwable) {
            archive.delete()
            throw error
        } finally {
            stagedApk.delete()
            outputDir.deleteRecursively()
        }
    }

    private suspend fun saveWithProgress(
        jadx: JadxDecompiler,
        onProgress: (done: Long, total: Long) -> Unit,
        shouldCancel: () -> Boolean
    ) {
        val executor = jadx.saveTaskExecutor
        executor.execute()
        try {
            while (executor.isRunning) {
                currentCoroutineContext().ensureActive()
                if (shouldCancel()) {
                    executor.terminate()
                    break
                }
                onProgress(executor.progress.toLong(), executor.tasksCount.toLong())
                delay(500L)
            }
        } catch (cancelled: CancellationException) {
            executor.terminate()
            executor.awaitTermination()
            throw cancelled
        }
        executor.awaitTermination()
        if (shouldCancel()) throw CancellationException("تم إلغاء التفكيك.")
    }

    private suspend fun copySelectedApk(context: Context, uri: Uri, destination: File) {
        val knownSize = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        require(knownSize <= MAX_APK_BYTES || knownSize < 0) {
            "حجم APK يتجاوز الحد الوقائي ${MAX_APK_BYTES / (1024L * 1024L)} MB."
        }

        var total = 0L
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("تعذر فتح APK المحدد.")
        input.use { source ->
            FileOutputStream(destination).buffered().use { sink ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_APK_BYTES) {
                        "تجاوز APK الحد الوقائي أثناء القراءة."
                    }
                    sink.write(buffer, 0, count)
                }
            }
        }
        require(total > 0L) { "ملف APK فارغ." }
    }

    @Suppress("DEPRECATION")
    private fun readPackageName(context: Context, apk: File): String {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: throw IllegalArgumentException("Android لم يتعرّف إلى الملف على أنه APK صالح.")
        return info.packageName
    }
}
