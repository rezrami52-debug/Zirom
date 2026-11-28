package com.racha.ai.core.security

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import com.racha.ai.core.shizuku.ShizukuShellRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class AppNetworkUsage(
    val appName: String,
    val packageName: String,
    val totalBytes: Long,
    val formattedUsage: String
)

class NetworkUsageManager(private val context: Context) {

    private val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager

    /**
     * التحقق من الصلاحية أو منحها تلقائياً عبر Shizuku
     */
    @Suppress("DEPRECATION")
    private fun ensureUsageStatsPermission() {
        if (ShizukuShellRunner.isShizukuAvailable()) {
            ShizukuShellRunner.execCommand("appops set ${context.packageName} GET_USAGE_STATS allow")
        }

        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val mode = try {
            appOps?.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } catch (_: Exception) {
            null
        }
        if (mode != AppOpsManager.MODE_ALLOWED) {
            throw SecurityException(
                "إذن إحصاءات الاستخدام غير مفعّل. فعّل وصول الاستخدام لـ Racha من إعدادات التطبيقات الخاصة."
            )
        }
    }

    @Suppress("DEPRECATION")
    suspend fun getTodayTopDataConsumers(limit: Int = 5): List<AppNetworkUsage> = withContext(Dispatchers.IO) {
        ensureUsageStatsPermission()
        val list = mutableListOf<AppNetworkUsage>()
        if (networkStatsManager == null) throw IllegalStateException("NetworkStatsManager غير متاح")

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = cal.timeInMillis
        val endTime = System.currentTimeMillis()

        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val uidToAppMap = mutableMapOf<Int, ApplicationInfo>()
        for (app in installedApps) {
            uidToAppMap[app.uid] = app
        }

        val usageMap = mutableMapOf<Int, Long>()

        // استهلاك الواي فاي وبيانات الهاتف منذ منتصف الليل.
        accumulateTraffic(ConnectivityManager.TYPE_WIFI, startTime, endTime, usageMap)
        accumulateTraffic(ConnectivityManager.TYPE_MOBILE, startTime, endTime, usageMap)

        for ((uid, bytes) in usageMap) {
            val appInfo = uidToAppMap[uid] ?: continue
            val appName = pm.getApplicationLabel(appInfo).toString()
            if (bytes > 1024 * 1024) { // أكثر من 1 ميغابايت
                list.add(
                    AppNetworkUsage(
                        appName = appName,
                        packageName = appInfo.packageName,
                        totalBytes = bytes,
                        formattedUsage = formatBytes(bytes)
                    )
                )
            }
        }

        list.sortedByDescending { it.totalBytes }.take(limit)
    }

    private fun accumulateTraffic(
        networkType: Int,
        startTime: Long,
        endTime: Long,
        outMap: MutableMap<Int, Long>
    ) {
        val stats = networkStatsManager?.querySummary(networkType, null, startTime, endTime)
            ?: throw IllegalStateException("تعذر فتح إحصاءات الشبكة")
        try {
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val bytes = bucket.rxBytes + bucket.txBytes
                outMap[uid] = (outMap[uid] ?: 0L) + bytes
            }
        } finally {
            stats.close()
        }
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(java.util.Locale.US, "%.1f MB", mb)
        }
    }
}
