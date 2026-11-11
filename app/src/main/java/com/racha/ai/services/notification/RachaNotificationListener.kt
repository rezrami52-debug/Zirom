package com.racha.ai.services.notification

import android.app.KeyguardManager
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.racha.ai.core.eve.EveBubbleService
import com.racha.ai.core.eve.EveState
import com.racha.ai.core.eve.MediaShelfType
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.data.repository.NotificationRepository
import com.racha.ai.domain.model.AppNotification

class RachaNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "RachaNotif"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        if (sbn.packageName == packageName) return

        val notification = sbn.notification ?: return

        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
            return
        }

        val extras = notification.extras ?: return

        val title = extras
            .getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            ?.trim()
            ?: ""

        val text = extras
            .getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            ?.trim()
            ?: ""

        if (title.isBlank() && text.isBlank()) return

        val ignoredApps = setOf(
            "com.android.systemui",
            "android",
            "com.miui.systemui"
        )

        if (sbn.packageName in ignoredApps) return

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(
                sbn.packageName,
                0
            )
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        val appNotification = AppNotification(
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        NotificationRepository.add(appNotification)

        val speechText = buildSpeechText(
            appName = appName,
            title = title,
            text = text
        )

        val prefs = AppPreferences(this)

        if (!prefs.isEveBubbleEnabled()) {
            return
        }

        val screenIsLocked = isScreenLocked()

        val eveIntent = Intent(
            this,
            EveBubbleService::class.java
        ).apply {
            action = EveBubbleService.ACTION_NEW_NOTIFICATION
            putExtra(EveBubbleService.EXTRA_SPEECH_TEXT, speechText)
            putExtra(EveBubbleService.EXTRA_NOTIF_KEY, sbn.key)

            // الشاشة مفتوحة: عرض فقط بدون كلام.
            putExtra(
                EveBubbleService.EXTRA_SILENT_NOTIFICATION,
                !screenIsLocked
            )
        }

        try {
            startService(eveIntent)
            Log.d(
                TAG,
                if (screenIsLocked) {
                    "إشعار مع نطق لأن الشاشة مغلقة"
                } else {
                    "إشعار صامت لأن الشاشة مفتوحة"
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "فشل إرسال الإشعار إلى EVE", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return

        val cancelIntent = Intent(
            this,
            EveBubbleService::class.java
        ).apply {
            action = EveBubbleService.ACTION_CANCEL_NOTIFICATION
            putExtra(EveBubbleService.EXTRA_NOTIF_KEY, sbn.key)
        }

        try {
            startService(cancelIntent)
        } catch (e: Exception) {
            Log.e(TAG, "فشل إلغاء إشعار EVE", e)
        }
    }

    private fun isScreenLocked(): Boolean {
        val powerManager = getSystemService(
            Context.POWER_SERVICE
        ) as PowerManager

        val keyguardManager = getSystemService(
            Context.KEYGUARD_SERVICE
        ) as KeyguardManager

        return !powerManager.isInteractive ||
            keyguardManager.isKeyguardLocked
    }

    private fun buildSpeechText(
        appName: String,
        title: String,
        text: String
    ): String {
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
