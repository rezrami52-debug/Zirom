package com.racha.ai.core.apps

import android.content.Context
import android.util.Log
import com.racha.ai.services.location.LocationHelper

/**
 * معالج النوايا — يربط IntentClassifier + AppResolver.
 */
class IntentHandler(
    private val context: Context,
    private val appResolver: AppResolver
) {

    companion object {
        private const val TAG = "RachaIntentHandler"
    }

    /**
     * نتيجة معالجة النية.
     */
    sealed class HandlerResult {
        /** رد فوري (بدون حوار). */
        data class DirectReply(val message: String) : HandlerResult()

        /** حوار مطلوب (لاختيار تطبيق). */
        data class ShowChooser(
            val title: String,
            val apps: List<AppResolver.InstalledApp>,
            val pendingAction: PendingAction
        ) : HandlerResult()

        /** خطأ. */
        data class Error(val message: String) : HandlerResult()

        /** لم تُفهم النية — اذهب لـ LLM. */
        object Unknown : HandlerResult()
    }

    /**
     * معالجة النص.
     */
    suspend fun handle(text: String): HandlerResult {
        val intentType = IntentClassifier.classify(text)
        Log.d(TAG, "نية: $intentType")

        return when (intentType) {
            IntentClassifier.IntentType.SHOW_LOCATION -> handleShowLocation()
            IntentClassifier.IntentType.OPEN_MAP -> handleOpenMap()
            IntentClassifier.IntentType.SEND_MESSAGE -> handleSendMessage(text)
            IntentClassifier.IntentType.MAKE_CALL -> handleMakeCall(text)
            IntentClassifier.IntentType.SEND_EMAIL -> handleSendEmail(text)
            IntentClassifier.IntentType.OPEN_BROWSER -> handleOpenBrowser()
            IntentClassifier.IntentType.OPEN_CAMERA -> handleOpenCamera()
            IntentClassifier.IntentType.PLAY_MUSIC -> handlePlayMusic()
            IntentClassifier.IntentType.OPEN_APP -> handleOpenApp(text)
            IntentClassifier.IntentType.UNKNOWN -> HandlerResult.Unknown
        }
    }

    /**
     * "اين انا" — إجابة نصية.
     */
    private suspend fun handleShowLocation(): HandlerResult {
        val location = LocationHelper.getCurrentLocation(context)
            ?: return HandlerResult.Error("⚠️ لا أستطيع الوصول لموقعك.")

        val city = location.cityName ?: "منطقتك"
        val lat = String.format("%.4f", location.latitude)
        val lon = String.format("%.4f", location.longitude)
        return HandlerResult.DirectReply("📍 أنت في: $city\n($lat, $lon)")
    }

    /**
     * "اعرض موقعي على الخريطة" — يعرض التطبيقات.
     */
    private suspend fun handleOpenMap(): HandlerResult {
        val location = LocationHelper.getCurrentLocation(context)
            ?: return HandlerResult.Error("⚠️ لا أستطيع الوصول لموقعك.")

        val mapApps = appResolver.getMapApps()

        if (mapApps.isEmpty()) {
            return HandlerResult.Error("⚠️ لا يوجد تطبيق خرائط مثبت.\n" +
                "الإحداثيات: (${location.latitude}, ${location.longitude})")
        }

        if (mapApps.size == 1) {
            // تطبيق واحد — نفتح مباشرة
            return directOpenMap(mapApps[0], location)
        }

        // عدة تطبيقات — نعرض الحوار
        return HandlerResult.ShowChooser(
            title = "🗺️ اختر تطبيق الخرائط",
            apps = mapApps,
            pendingAction = PendingAction(
                type = "MAP",
                lat = location.latitude,
                lon = location.longitude
            )
        )
    }

    /**
     * فتح الخريطة مباشرة.
     */
    private fun directOpenMap(
        app: AppResolver.InstalledApp,
        location: LocationHelper.LocationData
    ): HandlerResult {
        val success = appResolver.openMapsWithCoords(
            app.packageName,
            location.latitude,
            location.longitude
        )
        return if (success) {
            HandlerResult.DirectReply("🗺️ تم فتح ${app.appName}")
        } else {
            HandlerResult.Error("⚠️ فشل فتح ${app.appName}")
        }
    }

    /**
     * "أرسل رسالة لـ أحمد".
     */
    private suspend fun handleSendMessage(text: String): HandlerResult {
        val messagingApps = appResolver.getMessagingApps()

        if (messagingApps.isEmpty()) {
            return HandlerResult.Error("⚠️ لا يوجد تطبيق مراسلة مثبت.")
        }

        // استخراج اسم الشخص (بسيط)
        val contactName = extractName(text)

        if (messagingApps.size == 1) {
            return openMessagingApp(messagingApps[0], contactName)
        }

        return HandlerResult.ShowChooser(
            title = "💬 اختر تطبيق المراسلة",
            apps = messagingApps,
            pendingAction = PendingAction(
                type = "MESSAGE",
                contactName = contactName
            )
        )
    }

    /**
     * فتح تطبيق مراسلة.
     */
    private fun openMessagingApp(
        app: AppResolver.InstalledApp,
        contactName: String?
    ): HandlerResult {
        val success = appResolver.openApp(app.packageName)
        return if (success) {
            val extra = if (contactName != null) "\nابحث عن: $contactName" else ""
            HandlerResult.DirectReply("💬 تم فتح ${app.appName}$extra")
        } else {
            HandlerResult.Error("⚠️ فشل فتح ${app.appName}")
        }
    }

    /**
     * "اتصل بأحمد".
     */
    private suspend fun handleMakeCall(text: String): HandlerResult {
        val contactName = extractName(text)
        return HandlerResult.DirectReply(
            "📞 لفتح الاتصال بـ ${contactName ?: "شخص"}، " +
            "افتح تطبيق الهاتف وابحث عنه."
        )
    }

    /**
     * "أرسل إيميل".
     */
    private suspend fun handleSendEmail(text: String): HandlerResult {
        val emailApps = appResolver.getEmailApps()

        if (emailApps.isEmpty()) {
            return HandlerResult.Error("⚠️ لا يوجد تطبيق بريد مثبت.")
        }

        if (emailApps.size == 1) {
            appResolver.openApp(emailApps[0].packageName)
            return HandlerResult.DirectReply("📧 تم فتح ${emailApps[0].appName}")
        }

        return HandlerResult.ShowChooser(
            title = "📧 اختر تطبيق البريد",
            apps = emailApps,
            pendingAction = PendingAction(type = "EMAIL")
        )
    }

    /**
     * "افتح المتصفح".
     */
    private suspend fun handleOpenBrowser(): HandlerResult {
        val browserApps = appResolver.getBrowserApps()

        if (browserApps.isEmpty()) {
            return HandlerResult.Error("⚠️ لا يوجد متصفح مثبت.")
        }

        if (browserApps.size == 1) {
            appResolver.openApp(browserApps[0].packageName)
            return HandlerResult.DirectReply("🌐 تم فتح ${browserApps[0].appName}")
        }

        return HandlerResult.ShowChooser(
            title = "🌐 اختر المتصفح",
            apps = browserApps,
            pendingAction = PendingAction(type = "BROWSER")
        )
    }

    /**
     * "افتح الكاميرا".
     */
    private suspend fun handleOpenCamera(): HandlerResult {
        return HandlerResult.Error("⚠️ لفتح الكاميرا، استخدم الأمر الصريح.")
    }

    /**
     * "شغل موسيقى".
     */
    private suspend fun handlePlayMusic(): HandlerResult {
        return HandlerResult.Error("⚠️ هذه الميزة قيد التطوير.")
    }

    /**
     * "افتح تطبيق X".
     */
    private suspend fun handleOpenApp(text: String): HandlerResult {
        return HandlerResult.Error("⚠️ أخبرني باسم التطبيق بدقة.")
    }

    /**
     * تنفيذ الإجراء المعلّق بعد اختيار المستخدم.
     */
    fun executeChoice(
        app: AppResolver.InstalledApp,
        action: PendingAction
    ): String {
        return when (action.type) {
            "MAP" -> {
                if (action.lat != null && action.lon != null) {
                    val ok = appResolver.openMapsWithCoords(
                        app.packageName, action.lat, action.lon
                    )
                    if (ok) "🗺️ تم فتح ${app.appName}"
                    else "⚠️ فشل فتح ${app.appName}"
                } else {
                    "⚠️ لا توجد إحداثيات"
                }
            }
            "MESSAGE", "EMAIL" -> {
                val ok = appResolver.openApp(app.packageName)
                if (ok) "✅ تم فتح ${app.appName}"
                else "⚠️ فشل فتح ${app.appName}"
            }
            else -> {
                val ok = appResolver.openApp(app.packageName)
                if (ok) "✅ تم فتح ${app.appName}"
                else "⚠️ فشل فتح ${app.appName}"
            }
        }
    }

    /**
     * استخراج اسم من النص.
     */
    private fun extractName(text: String): String? {
        // بسيط: نأخذ آخر كلمة بعد "لـ" أو "بـ"
        val patterns = listOf("لـ", "ل ", "بـ", "ب ")
        for (pattern in patterns) {
            val idx = text.indexOf(pattern)
            if (idx >= 0) {
                val rest = text.substring(idx + pattern.length).trim()
                if (rest.isNotBlank()) return rest.split(" ").first()
            }
        }
        return null
    }
}
