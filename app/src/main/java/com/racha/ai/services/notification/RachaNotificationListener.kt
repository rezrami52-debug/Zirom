package com.racha.ai.services.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.racha.ai.data.repository.NotificationRepository
import com.racha.ai.domain.model.AppNotification

class RachaNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // تجاهل إشعاراتنا
        if (sbn.packageName == packageName) return

        // استخرج البيانات
        val extras = sbn.notification?.extras ?: return

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

        val notification = AppNotification(
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        NotificationRepository.add(notification)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // لا نفعل شيئاً حالياً
    }
}
