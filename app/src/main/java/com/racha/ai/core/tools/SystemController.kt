package com.racha.ai.core.tools

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.racha.ai.core.apps.AppResolver

class SystemController(private val context: Context) {

    companion object {
        private const val TAG = "RachaSystemController"
    }

    /**
     * التحكم في الواي فاي / الإنترنت
     */
    fun toggleInternet(): String {
        return try {
            // الحل القانوني الوحيد لأندرويد 10+: فتح لوحة تحكم الإنترنت
            val intent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "🌐 جاري فتح لوحة التحكم بالإنترنت والواي فاي. يمكنك التبديل من هناك بضغطة واحدة."
        } catch (e: Exception) {
            "⚠️ فشل فتح إعدادات الشبكة."
        }
    }

    /**
     * إغلاق تطبيق معين (في الخلفية فقط)
     */
    fun closeApp(appName: String): String {
        val appResolver = AppResolver(context)
        val allApps = appResolver.getAllInstalledApps()
        
        // البحث عن التطبيق بأقرب تطابق للاسم
        val targetApp = allApps.find { it.appName.contains(appName, ignoreCase = true) }
        
        if (targetApp == null) {
            return "⚠️ لم أجد تطبيقاً باسم '$appName' لإغلاقه."
        }

        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.killBackgroundProcesses(targetApp.packageName)
            "✅ جاري محاولة إغلاق تطبيق '${targetApp.appName}' في الخلفية."
        } catch (e: Exception) {
            Log.e(TAG, "فشل إغلاق التطبيق", e)
            "⚠️ فشل إغلاق التطبيق. قد يكون يعمل في المقدمة أو يحتاج صلاحيات خاصة."
        }
    }

    /**
     * إقفال الشاشة (Lock Screen)
     */
    fun lockScreen(): String {
        return try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(context, DeviceAdminReceiver::class.java) // سننشئه أدناه
            
            if (devicePolicyManager.isAdminActive(componentName)) {
                devicePolicyManager.lockNow()
                "🔒 جاري إقفال الشاشة الآن."
            } else {
                "⚠️ للقيام بذلك، يجب تفعيل 'مسؤول الجهاز' للتطبيق.\nاذهب إلى: الإعدادات > الأمان > تطبيقات مسؤول الجهاز > فعّل Racha."
            }
        } catch (e: SecurityException) {
            "⚠️ الصلاحية غير ممنوحة. يرجى تفعيل التطبيق كمسؤول للجهاز من الإعدادات."
        } catch (e: Exception) {
            "⚠️ فشل إقفال الشاشة."
        }
    }
}
