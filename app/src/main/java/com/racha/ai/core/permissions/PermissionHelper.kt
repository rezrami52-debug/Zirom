package com.racha.ai.core.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * أدوات للتحقق من الصلاحيات الحساسة وفتح إعداداتها.
 */
object PermissionHelper {

    /**
     * هل Racha لديه صلاحية قراءة الإشعارات؟
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        val packageName = context.packageName
        return flat.contains(packageName)
    }

    /**
     * فتح إعدادات "الوصول إلى الإشعارات".
     * على MIUI، هذه الصفحة قد تكون مخفية - سنستخدم Intent المباشر.
     */
    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // فشل - نحاول صفحة إعدادات التطبيق
            openAppSettings(context)
        }
    }

    /**
     * فتح إعدادات التطبيق (معلومات Racha).
     * هنا يمكن للمستخدم إلغاء Restricted Settings.
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * فتح إعدادات الموقع العامة.
     */
    fun openLocationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * فتح صفحة "الصلاحيات الخاصة" أو "Restricted Settings".
     * متاح على Android 10+.
     */
    fun openSpecialAppAccess(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    /**
     * هل Racha مثبت من مصدر غير معروف (sideloaded)؟
     */
    fun isInstalledFromUnknownSource(context: Context): Boolean {
        return try {
            val installer = context.packageManager
                .getInstallSourceInfo(context.packageName)
                .installingPackageName
            installer == null || installer != "com.android.vending"
        } catch (e: Exception) {
            true
        }
    }
}
