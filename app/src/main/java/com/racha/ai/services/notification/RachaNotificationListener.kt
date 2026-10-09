package com.racha.ai.services.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.racha.ai.data.repository.NotificationRepository
import com.racha.ai.domain.model.AppNotification
import com.racha.ai.services.tts.TextToSpeechHelper

class RachaNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "RachaNotif"
    }

    override fun onCreate() {
        super.onCreate()
        TextToSpeechHelper.initialize(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // تجاهل إشعاراتنا
        if (sbn.packageName == packageName) return

        // تجاهل الإشعارات الصامتة (مثل تشغيل الموسيقى)
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // تجاهل الإشعارات الفارغة
        if (title.isBlank() && text.isBlank()) return

        // اسم التطبيق
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        // تجاهل التطبيقات التي لا تهمّ (اختياري - يمكن تعديله لاحقاً)
        val ignoredApps = setOf(
            "com.android.systemui",
            "android",
            "com.miui.systemui"
        )
        if (sbn.packageName in ignoredApps) return

        val appNotification = AppNotification(
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        // 1. حفظ في Repository
        NotificationRepository.add(appNotification)

        // 2. قراءة بصوت
        val speechText = buildSpeechText(appName, title, text)
        Log.d(TAG, "🔊 قراءة: $speechText")
        TextToSpeechHelper.speak(speechText)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // لا نفعل شيئاً حالياً
    }

    /**
     * بناء النص الصوتي للإشعار.
     * التنسيق: "من [التطبيق]: [العنوان]. [النص]"
     */
    private fun buildSpeechText(appName: String, title: String, text: String): String {
        val parts = mutableListOf<String>()

        // اسم التطبيق (نترجم الأشهر)
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

    override fun onDestroy() {
        super.onDestroy()
        // لا نُغلق TTS هنا - لأن Singleton يُستخدم في مكان آخر
    }
}
