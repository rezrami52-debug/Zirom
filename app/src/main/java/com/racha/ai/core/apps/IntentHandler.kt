package com.racha.ai.core.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
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
        Log.d(TAG, "نية: $intentType من نص: $text")

        return when (intentType) {
            IntentClassifier.IntentType.SHOW_LOCATION -> handleShowLocation()
            IntentClassifier.IntentType.OPEN_MAP -> handleOpenMap()
            IntentClassifier.IntentType.SEND_MESSAGE -> handleSendMessage(text)
            IntentClassifier.IntentType.MAKE_CALL -> handleMakeCall(text)
            IntentClassifier.IntentType.OPEN_BROWSER -> handleOpenBrowser()
            IntentClassifier.IntentType.OPEN_CAMERA -> handleOpenCamera()
            IntentClassifier.IntentType.LIST_APPS -> handleListApps()
            else -> HandlerResult.Unknown
        }
    }

    private suspend fun handleShowLocation(): HandlerResult {
        val location = LocationHelper.getCurrentLocation(context)
            ?: return HandlerResult.Error("⚠️ لا أستطيع الوصول لموقعك. تأكد من تفعيل GPS.")

        val city = location.cityName ?: "منطقتك"
        return HandlerResult.DirectReply("📍 أنت في: $city\nالإحداثيات: (${location.latitude}, ${location.longitude})")
    }

    private suspend fun handleOpenMap(): HandlerResult {
        val location = LocationHelper.getCurrentLocation(context)
            ?: return HandlerResult.Error("⚠️ لا أستطيع الوصول لموقعك.")

        val mapApps = appResolver.getMapApps()
        if (mapApps.isEmpty()) {
            return HandlerResult.Error("⚠️ لا يوجد تطبيق خرائط مثبت.")
        }

        val preferredApp = mapApps.find {
            it.packageName == "com.google.android.apps.maps" ||
            it.packageName == "com.google.earth"
        } ?: mapApps.first()

        val success = appResolver.openMapsWithCoords(
            preferredApp.packageName,
            location.latitude,
            location.longitude
        )

        return if (success) {
            HandlerResult.DirectReply("🗺️ تم فتح ${preferredApp.appName}")
        } else {
            try {
                val uri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                HandlerResult.DirectReply("🗺️ تم فتح الخريطة")
            } catch (e: Exception) {
                HandlerResult.Error("⚠️ فشل فتح الخريطة")
            }
        }
    }

    private suspend fun handleSendMessage(text: String): HandlerResult {
        val contactName = extractName(text) ?: "محدد"

        val contacts = contactResolver.searchContact(contactName)
        if (contacts.isEmpty()) {
            return HandlerResult.Error(
                "⚠️ لم أجد جهة اتصال باسم '$contactName'.\n" +
                "تأكد من:\n" +
                "1. وجود الاسم في جهات الاتصال\n" +
                "2. منح التطبيق صلاحية قراءة جهات الاتصال"
            )
        }

        val contactsInfo = contacts.joinToString("\n") { "👤 ${it.name}\n📞 ${it.phoneNumber}" }

        val messagingApps = appResolver.getMessagingApps()
        if (messagingApps.isEmpty()) {
            return HandlerResult.Error("⚠️ لا يوجد تطبيق مراسلة مثبت.\n\n$contactsInfo")
        }

        if (messagingApps.size == 1) {
            appResolver.openApp(messagingApps[0].packageName)
            return HandlerResult.DirectReply("✅ وجدت:\n$contactsInfo\n\nتم فتح ${messagingApps[0].appName}")
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

        val phoneRegex = Regex("\\d{8,}")
        val phoneNumber = phoneRegex.find(text)?.value

        if (phoneNumber != null) {
            return makePhoneCall(phoneNumber)
        }

        if (contactName != null) {
            val contacts = contactResolver.searchContact(contactName)
            if (contacts.isEmpty()) {
                return HandlerResult.Error(
                    "⚠️ لم أجد جهة اتصال باسم '$contactName'.\n" +
                    "يمكنك:\n" +
                    "1. إدخال الرقم مباشرة\n" +
                    "2. التأكد من وجود الاسم في جهات الاتصال"
                )
            }

            if (contacts.size == 1) {
                return makePhoneCall(contacts[0].phoneNumber)
            }

            val contactsInfo = contacts.joinToString("\n") { "👤 ${it.name} - 📞 ${it.phoneNumber}" }
            return HandlerResult.DirectReply("وجدت عدة جهات اتصال:\n$contactsInfo\n\nمن تريد الاتصال به؟")
        }

        return HandlerResult.Error("⚠️ أدخل اسم جهة الاتصال أو الرقم مباشرة")
    }

    private fun makePhoneCall(phoneNumber: String): HandlerResult {
        return HandlerResult.DirectReply("📞 جاري الاتصال بـ $phoneNumber...").also {
            try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "فشل الاتصال", e)
            }
        }
    }

    private suspend fun handleOpenBrowser(): HandlerResult {
        val browserApps = appResolver.getBrowserApps()
        if (browserApps.isEmpty()) {
            return HandlerResult.Error("⚠️ لا يوجد متصفح مثبت.")
        }

        appResolver.openApp(browserApps.first().packageName)
        return HandlerResult.DirectReply("🌐 تم فتح ${browserApps.first().appName}")
    }

    private suspend fun handleOpenCamera(): HandlerResult {
        return HandlerResult.DirectReply("📷 جاري فتح الكاميرا...").also {
            try {
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "فشل فتح الكاميرا", e)
            }
        }
    }

    private suspend fun handleListApps(): HandlerResult {
        val allApps = appResolver.getAllInstalledApps()

        if (allApps.isEmpty()) {
            return HandlerResult.Error("⚠️ لم أتمكن من جلب قائمة التطبيقات.")
        }

        val totalCount = allApps.size
        val previewApps = allApps.take(30)

        val appsList = previewApps.joinToString("\n") { app ->
            "• ${app.appName}"
        }

        val moreText = if (totalCount > 30) {
            "\n\n... و ${totalCount - 30} تطبيق آخر"
        } else {
            ""
        }

        return HandlerResult.DirectReply(
            "📱 لديك $totalCount تطبيق مثبت:\n\n" +
            appsList +
            moreText
        )
    }

    fun executeChoice(app: AppResolver.InstalledApp, action: PendingAction): String {
        return when (action.type) {
            "MESSAGE" -> {
                val details = action.contactDetails ?: ""
                appResolver.openApp(app.packageName)
                "✅ جاري فتح ${app.appName} لمراسلة:\n$details"
            }
            "MAP" -> {
                if (action.lat != null && action.lon != null) {
                    val ok = appResolver.openMapsWithCoords(app.packageName, action.lat, action.lon)
                    if (ok) "🗺️ تم فتح ${app.appName}" else "⚠️ فشل فتح ${app.appName}"
                } else "⚠️ لا توجد إحداثيات"
            }
            else -> {
                if (appResolver.openApp(app.packageName))
                    "✅ تم فتح ${app.appName}"
                else
                    "⚠️ فشل فتح ${app.appName}"
            }
        }
    }

    private fun extractName(text: String): String? {
        val patterns = listOf("لـ", "ل ", "بـ", "ب ", "الى ", "إلى ", "مع ")
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
