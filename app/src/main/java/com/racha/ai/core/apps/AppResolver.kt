package com.racha.ai.core.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

class AppResolver(private val context: Context) {

    companion object {
        private const val TAG = "RachaAppResolver"
    }

    data class InstalledApp(
        val packageName: String,
        val appName: String
    )

    enum class AppCategory(val arabic: String) {
        MAPS("الخرائط"),
        MESSAGING("المراسلة"),
        EMAIL("البريد"),
        PHONE("الاتصال"),
        CAMERA("الكاميرا"),
        MUSIC("الموسيقى"),
        VIDEO("الفيديو"),
        BROWSER("المتصفح"),
        UNKNOWN("أخرى")
    }

    private val knownApps = mapOf(
        AppCategory.MAPS to listOf(
            "com.google.earth",
            "com.google.android.apps.maps",
            "net.osmand",
            "net.osmand.plus",
            "com.waze"
        ),
        AppCategory.MESSAGING to listOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.facebook.orca",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.viber.voip"
        ),
        AppCategory.EMAIL to listOf(
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail"
        ),
        AppCategory.BROWSER to listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.opera.browser"
        ),
        AppCategory.CAMERA to listOf(
            "com.android.camera",
            "com.google.android.GoogleCamera",
            "com.android.camera2"
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
        AppCategory.PHONE to listOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.contacts"
        )
    )

    fun findAppsForCategory(category: AppCategory): List<InstalledApp> {
        val knownPackages = knownApps[category] ?: return emptyList()
        val pm = context.packageManager
        val result = mutableListOf<InstalledApp>()

        for (pkg in knownPackages) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                result.add(
                    InstalledApp(
                        packageName = pkg,
                        appName = pm.getApplicationLabel(appInfo).toString()
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // غير مثبت
            }
        }
        return result
    }

    /**
     * جلب كل التطبيقات المثبتة (مع QUERY_ALL_PACKAGES).
     */
    fun getAllInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .map { resolveInfo ->
                    InstalledApp(
                        packageName = resolveInfo.activityInfo.packageName,
                        appName = resolveInfo.loadLabel(pm).toString()
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "فشل جلب التطبيقات", e)
            emptyList()
        }
    }

    fun getMapApps(): List<InstalledApp> = findAppsForCategory(AppCategory.MAPS)
    fun getMessagingApps(): List<InstalledApp> = findAppsForCategory(AppCategory.MESSAGING)
    fun getEmailApps(): List<InstalledApp> = findAppsForCategory(AppCategory.EMAIL)
    fun getBrowserApps(): List<InstalledApp> = findAppsForCategory(AppCategory.BROWSER)

    fun openApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager
                .getLaunchIntentForPackage(packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent != null) {
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "فشل فتح $packageName", e)
            false
        }
    }

    fun openMapsWithCoords(packageName: String, lat: Double, lon: Double): Boolean {
        return try {
            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(موقعي)")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "فشل فتح الخريطة", e)
            false
        }
    }
}
