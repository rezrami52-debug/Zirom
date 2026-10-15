package com.racha.ai.core.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.util.Log

/**
 * يبحث عن التطبيقات المثبتة ويصنّفها حسب الفئة.
 */
class AppResolver(private val context: Context) {

    companion object {
        private const val TAG = "RachaAppResolver"
    }

    /**
     * تطبيق مثبت.
     */
    data class InstalledApp(
        val packageName: String,
        val appName: String,
        val iconUri: String? = null
    )

    /**
     * فئة التطبيقات.
     */
    enum class AppCategory(val arabic: String) {
        MAPS("الخرائط"),
        MESSAGING("المراسلة"),
        EMAIL("البريد الإلكتروني"),
        PHONE("الاتصال"),
        CAMERA("الكاميرا"),
        MUSIC("الموسيقى"),
        VIDEO("الفيديو"),
        BROWSER("المتصفح"),
        NOTES("الملاحظات"),
        CALENDAR("التقويم"),
        UNKNOWN("غير معروف")
    }

    /**
     * التطبيقات المعروفة حسب الفئة (لكل فئة، قائمة بالحزم المعروفة).
     */
    private val knownApps = mapOf(
        AppCategory.MAPS to listOf(
            "com.google.earth",
            "com.google.android.apps.maps",
            "net.osmand",
            "net.osmand.plus",
            "com.waze",
            "com.mapfactor.navigator",
            "ru.yandex.yandexmaps"
        ),
        AppCategory.MESSAGING to listOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.telegram.plus",
            "com.facebook.orca",
            "com.instagram.android",
            "com.discord",
            "com.viber.voip",
            "com.google.android.apps.messaging",
            "com.android.mms"
        ),
        AppCategory.EMAIL to listOf(
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail",
            "com.samsung.android.email.provider"
        ),
        AppCategory.PHONE to listOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.contacts"
        ),
        AppCategory.CAMERA to listOf(
            "com.android.camera",
            "com.google.android.GoogleCamera",
            "com.android.camera2",
            "com.miui.camera",
            "com.samsung.android.camera"
        ),
        AppCategory.MUSIC to listOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.soundcloud.android",
            "org.videolan.vlc"
        ),
        AppCategory.VIDEO to listOf(
            "com.google.android.youtube",
            "com.mxtech.videoplayer.ad",
            "org.videolan.vlc"
        ),
        AppCategory.BROWSER to listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.opera.browser",
            "com.sec.android.app.sbrowser"
        ),
        AppCategory.NOTES to listOf(
            "com.google.android.keep",
            "notion.id",
            "com.samsung.android.app.notes"
        ),
        AppCategory.CALENDAR to listOf(
            "com.google.android.calendar",
            "com.samsung.android.calendar"
        )
    )

    /**
     * إيجاد التطبيقات المثبتة لفئة معينة.
     */
    fun findAppsForCategory(category: AppCategory): List<InstalledApp> {
        val knownPackages = knownApps[category] ?: return emptyList()
        val pm = context.packageManager
        val result = mutableListOf<InstalledApp>()

        for (pkg in knownPackages) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                result.add(
                    InstalledApp(
                        packageName = pkg,
                        appName = appName
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // غير مثبت — تخطّى
            }
        }

        Log.d(TAG, "${category.arabic}: ${result.size} تطبيق")
        return result
    }

    /**
     * كل التطبيقات القادرة على فتح رابط/نية (عام).
     */
    fun findAppsForIntent(intent: Intent): List<InstalledApp> {
        val pm = context.packageManager
        val activities: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

        return activities
            .map { resolveInfo ->
                InstalledApp(
                    packageName = resolveInfo.activityInfo.packageName,
                    appName = resolveInfo.loadLabel(pm).toString()
                )
            }
            .distinctBy { it.packageName }
    }

    /**
     * تطبيقات الخرائط المثبتة.
     */
    fun getMapApps(): List<InstalledApp> = findAppsForCategory(AppCategory.MAPS)

    /**
     * تطبيقات المراسلة المثبتة.
     */
    fun getMessagingApps(): List<InstalledApp> = findAppsForCategory(AppCategory.MESSAGING)

    /**
     * تطبيقات البريد المثبتة.
     */
    fun getEmailApps(): List<InstalledApp> = findAppsForCategory(AppCategory.EMAIL)

    /**
     * تطبيقات المتصفح المثبتة.
     */
    fun getBrowserApps(): List<InstalledApp> = findAppsForCategory(AppCategory.BROWSER)

    /**
     * فتح تطبيق بالحزمة.
     */
    fun openApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "فشل فتح $packageName", e)
            false
        }
    }

    /**
     * فتح خريطة مع إحداثيات.
     */
    fun openMapsWithCoords(packageName: String, lat: Double, lon: Double): Boolean {
        return try {
            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "فشل فتح الخريطة في $packageName", e)
            false
        }
    }
}
