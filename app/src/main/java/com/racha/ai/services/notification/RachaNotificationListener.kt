package com.racha.ai.services.notification

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.racha.ai.core.eve.EveBubbleService
import com.racha.ai.data.repository.NotificationRepository
import com.racha.ai.domain.model.AppNotification

class RachaNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "RachaNotif"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // تجاهل إشعارات تطبيقنا
        if (sbn.packageName == packageName) return

        // تجاهل الإشعارات المستمرة (مثل مشغلات الوسائط وعدادات التحميل)
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        val ignoredApps = setOf("com.android.systemui", "android", "com.miui.systemui")
        if (sbn.packageName in ignoredApps) return

        val appNotification = AppNotification(
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        // 1. حفظ في المستودع
        NotificationRepository.add(appNotification)

        // 2. بناء النص الصوتي
        val speechText = buildSpeechText(appName, title, text)
        Log.d(TAG, "إرسال إشعار لـ EVE: $speechText")

        // 3. إرسال أمر التنبيه الذكي لـ EVE
        val eveIntent = Intent(this, EveBubbleService::class.java).apply {
            action = EveBubbleService.ACTION_NEW_NOTIFICATION
            putExtra(EveBubbleService.EXTRA_SPEECH_TEXT, speechText)
            putExtra(EveBubbleService.EXTRA_NOTIF_KEY, sbn.key)
        }
        try {
            startService(eveIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        // إذا قام المستخدم بمسح الإشعار يدوياً، نلغي تنبيه EVE
        val cancelIntent = Intent(this, EveBubbleService::class.java).apply {
            action = EveBubbleService.ACTION_CANCEL_NOTIFICATION
            putExtra(EveBubbleService.EXTRA_NOTIF_KEY, sbn.key)
        }
        try {
            startService(cancelIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildSpeechText(appName: String, title: String, text: String): String {
        val parts = mutableListOf<String>()

        val localizedApp = when (appName.lowercase()) {
            "whatsapp" -> "واتساب"
            "telegram" -> "تيليجرام"
            "messages", "messaging" -> "الرسائل"
            "gmail" -> "جيميل"
            "phone", "dialer" -> "الهاتف"
            "calendar" -> "التقويم"
            "clock" -> "الساعة"
            "instagram" -> "إنستغرام"
            "facebook" -> "فيسبوك"
            "twitter", "x" -> "تويتر"
            else -> appName
        }
        parts.add("من $localizedApp")

        if (title.isNotBlank() && title != appName) {
            parts.add(title)
        }
        if (text.isNotBlank() && text != title) {
            parts.add(text)
        }

        return parts.joinToString(". ")
    }
}
