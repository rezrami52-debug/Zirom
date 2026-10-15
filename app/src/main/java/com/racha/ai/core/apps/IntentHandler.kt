package com.racha.ai.core.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.racha.ai.core.contacts.ContactResolver
import com.racha.ai.services.location.LocationHelper

class IntentHandler(
    private val context: Context,
    private val appResolver: AppResolver,
    private val contactResolver: ContactResolver
) {
    companion object {
        private const val TAG = "RachaIntentHandler"
    }

    sealed class HandlerResult {
        data class DirectReply(val message: String) : HandlerResult()
        data class ShowChooser(
            val title: String,
            val apps: List<AppResolver.InstalledApp>,
            val pendingAction: PendingAction
        ) : HandlerResult()
        data class Error(val message: String) : HandlerResult()
        object Unknown : HandlerResult()
    }

    suspend fun handle(text: String): HandlerResult {
        val intentType = IntentClassifier.classify(text)
        Log.d(TAG, "نية: $intentType")

        return when (intentType) {
            IntentClassifier.IntentType.SHOW_LOCATION -> handleShowLocation()
            IntentClassifier.IntentType.OPEN_MAP -> handleOpenMap()
            IntentClassifier.IntentType.SEND_MESSAGE -> handleSendMessage(text)
            IntentClassifier.IntentType.MAKE_CALL -> handleMakeCall(text)
            IntentClassifier.IntentType.SEND_EMAIL -> handleSendEmail()
            IntentClassifier.IntentType.OPEN_BROWSER -> handleOpenBrowser()
            IntentClassifier.IntentType.OPEN_CAMERA -> HandlerResult.Error("⚠️ استخدم الأمر الصريح للكاميرا.")
            IntentClassifier.IntentType.PLAY_MUSIC -> HandlerResult.Error("⚠️ قيد التطوير.")
            IntentClassifier.IntentType.OPEN_APP -> HandlerResult.Error("⚠️ أخبرني باسم التطبيق.")
            IntentClassifier.IntentType.UNKNOWN -> HandlerResult.Unknown
        }
    }

    private suspend fun handleShowLocation(): HandlerResult {
        val location = LocationHelper.getCurrentLocation(context)
            ?: return HandlerResult.Error("⚠️ لا أستطيع الوصول لموقعك.")

        val city = location.cityName ?: "منطقتك"
        val lat = String.format("%.4f", location.latitude)
        val lon = String.format("%.4f", location.longitude)
        return HandlerResult.DirectReply("📍 أنت في: $city\n($lat, $lon)")
    }

    private suspend fun handleOpenMap(): HandlerResult {
        val location = LocationHelper.getCurrentLocation(context)
            ?: return HandlerResult.Error("⚠️ لا أستطيع الوصول لموقعك.")

        val mapApps = appResolver.getMapApps()
        if (mapApps.isEmpty()) {
            return HandlerResult.Error("⚠️ لا يوجد تطبيق خرائط مثبت.")
        }

        if (mapApps.size == 1) {
            val ok = appResolver.openMapsWithCoords(
                mapApps[0].packageName, location.latitude, location.longitude
            )
            return if (ok) HandlerResult.DirectReply("🗺️ تم فتح ${mapApps[0].appName}")
                   else HandlerResult.Error("⚠️ فشل الفتح.")
        }

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

    private suspend fun handleSendMessage(text: String): HandlerResult {
        val contactName = extractName(text) ?: "محدد"

        if (!contactResolver.hasPermission()) {
            return HandlerResult.Error(
                "⚠️ لا أستطيع قراءة جهات الاتصال.\n\n" +
                "اذهب إلى:\nالإعدادات → التطبيقات → Racha → الصلاحيات\n" +
                "وفعّل 'جهات الاتصال'."
            )
        }

        val contacts = contactResolver.searchContact(contactName)
        if (contacts.isEmpty()) {
            return HandlerResult.Error(
                "⚠️ لم أجد جهة اتصال باسم '$contactName'.\n\n" +
                "تأكد من الاسم."
            )
        }

        val contactsInfo = contacts.joinToString("\n") {
            "👤 ${it.name}\n📞 ${it.phoneNumber}"
        }

        val messagingApps = appResolver.getMessagingApps()
        if (messagingApps.isEmpty()) {
            return HandlerResult.Error(
                "⚠️ لا يوجد تطبيق مراسلة مثبت.\n\n$contactsInfo"
            )
        }

        if (messagingApps.size == 1) {
            appResolver.openApp(messagingApps[0].packageName)
            return HandlerResult.DirectReply(
                "✅ وجدت:\n$contactsInfo\n\n" +
                "تم فتح ${messagingApps[0].appName}"
            )
        }

        return HandlerResult.ShowChooser(
            title = "💬 اختر تطبيق المراسلة لـ '$contactName'",
            apps = messagingApps,
            pendingAction = PendingAction(
                type = "MESSAGE",
                contactName = contactName,
                contactDetails = contactsInfo
            )
        )
    }

    private suspend fun handleMakeCall(text: String): HandlerResult {
        val contactName = extractName(text)
            ?: return HandlerResult.Error("⚠️ حدد من تريد الاتصال به.")

        if (!contactResolver.hasPermission()) {
            return HandlerResult.Error("⚠️ صلاحية جهات الاتصال مطلوبة.")
        }

        val contacts = contactResolver.searchContact(contactName)
        if (contacts.isEmpty()) {
            return HandlerResult.Error("⚠️ لم أجد '$contactName'.")
        }

        val contact = contacts.first()

        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${contact.phoneNumber}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            HandlerResult.DirectReply("📞 تم فتح الاتصال بـ ${contact.name}\n(${contact.phoneNumber})")
        } catch (e: Exception) {
            HandlerResult.Error("⚠️ فشل الاتصال: ${e.message}")
        }
    }

    private suspend fun handleSendEmail(): HandlerResult {
        val emailApps = appResolver.getEmailApps()
        if (emailApps.isEmpty()) return HandlerResult.Error("⚠️ لا يوجد تطبيق بريد.")

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

    private suspend fun handleOpenBrowser(): HandlerResult {
        val browserApps = appResolver.getBrowserApps()
        if (browserApps.isEmpty()) return HandlerResult.Error("⚠️ لا يوجد متصفح.")

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

    fun executeChoice(app: AppResolver.InstalledApp, action: PendingAction): String {
        return when (action.type) {
            "MAP" -> {
                if (action.lat != null && action.lon != null) {
                    val ok = appResolver.openMapsWithCoords(
                        app.packageName, action.lat, action.lon
                    )
                    if (ok) "🗺️ تم فتح ${app.appName}"
                    else "⚠️ فشل فتح ${app.appName}"
                } else "⚠️ لا إحداثيات"
            }
            "MESSAGE" -> {
                val details = action.contactDetails ?: ""
                appResolver.openApp(app.packageName)
                "✅ تم فتح ${app.appName}\n\nجهة الاتصال:\n$details"
            }
            else -> {
                if (appResolver.openApp(app.packageName))
                    "✅ تم فتح ${app.appName}"
                else "⚠️ فشل فتح ${app.appName}"
            }
        }
    }

    private fun extractName(text: String): String? {
        val patterns = listOf("لـ", "ل ", "بـ", "ب ", "الى ", "إلى ")
        for (pattern in patterns) {
            val idx = text.indexOf(pattern)
            if (idx >= 0) {
                val rest = text.substring(idx + pattern.length).trim()
                if (rest.isNotBlank()) {
                    return rest.split(" ").firstOrNull { it.isNotBlank() }
                }
            }
        }
        return null
    }
}
