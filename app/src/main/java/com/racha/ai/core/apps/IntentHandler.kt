package com.racha.ai.core.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.racha.ai.core.contacts.ContactResolver
import com.racha.ai.core.tools.SystemController
import com.racha.ai.services.location.LocationHelper

class IntentHandler(
    private val context: Context,
    private val appResolver: AppResolver,
    private val contactResolver: ContactResolver,
    private val systemController: SystemController
) {
    companion object { private const val TAG = "RachaIntentHandler" }

    private val setAlarmHandler = SetAlarmHandler(context)

    sealed class HandlerResult {
        data class DirectReply(val message: String) : HandlerResult()
        data class ShowChooser(val title: String, val apps: List<AppResolver.InstalledApp>, val pendingAction: PendingAction) : HandlerResult()
        data class Error(val message: String) : HandlerResult()
        data class NeedsCorrection(val text: String) : HandlerResult()
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
            IntentClassifier.IntentType.SET_ALARM -> handleSetAlarm(text)
            IntentClassifier.IntentType.CORRECTION -> HandlerResult.NeedsCorrection(text)
            IntentClassifier.IntentType.TOGGLE_INTERNET -> HandlerResult.DirectReply(systemController.toggleInternet())
            IntentClassifier.IntentType.LOCK_SCREEN -> HandlerResult.DirectReply(systemController.lockScreen())
            IntentClassifier.IntentType.CLOSE_APP -> {
                val appName = extractName(text) ?: "محدد"
                HandlerResult.DirectReply(systemController.closeApp(appName))
            }
            IntentClassifier.IntentType.OPEN_APP -> handleOpenApp(text)
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
        if (mapApps.isEmpty()) return HandlerResult.Error("⚠️ لا يوجد تطبيق خرائط مثبت.")

        val preferredApp = mapApps.find { it.packageName == "com.google.android.apps.maps" || it.packageName == "com.google.earth" } ?: mapApps.first()
        val success = appResolver.openMapsWithCoords(preferredApp.packageName, location.latitude, location.longitude)

        return if (success) HandlerResult.DirectReply("🗺️ تم فتح ${preferredApp.appName}")
        else HandlerResult.Error("⚠️ فشل فتح الخريطة.")
    }

    private suspend fun handleSendMessage(text: String): HandlerResult {
        val contactName = extractName(text) ?: "محدد"
        val contacts = contactResolver.searchContact(contactName)
        
        if (contacts.isEmpty()) {
            return HandlerResult.Error("⚠️ لم أجد جهة اتصال باسم '$contactName'.\nتأكد من الاسم ومنح صلاحية 'جهات الاتصال'.")
        }

        val contactsInfo = contacts.joinToString("\n") { "👤 ${it.name}\n📞 ${it.phoneNumber}" }
        val messagingApps = appResolver.getMessagingApps()
        
        if (messagingApps.isEmpty()) return HandlerResult.Error("⚠️ لا يوجد تطبيق مراسلة مثبت.\n\n$contactsInfo")

        if (messagingApps.size == 1) {
            appResolver.openApp(messagingApps[0].packageName)
            return HandlerResult.DirectReply("✅ وجدت:\n$contactsInfo\n\nجاري فتح ${messagingApps[0].appName}...")
        }

        return HandlerResult.ShowChooser(
            title = "💬 اختر تطبيق المراسلة لـ:\n$contactsInfo",
            apps = messagingApps,
            pendingAction = PendingAction(type = "MESSAGE", contactName = contactName, contactDetails = contactsInfo)
        )
    }

    private suspend fun handleMakeCall(text: String): HandlerResult {
        val contactName = extractName(text)
        val phoneRegex = Regex("\\d{8,}")
        val phoneNumber = phoneRegex.find(text)?.value

        if (phoneNumber != null) return makePhoneCall(phoneNumber)

        if (contactName != null) {
            val contacts = contactResolver.searchContact(contactName)
            if (contacts.isEmpty()) return HandlerResult.Error("⚠️ لم أجد جهة اتصال باسم '$contactName'.")
            if (contacts.size == 1) return makePhoneCall(contacts[0].phoneNumber)
            
            val contactsInfo = contacts.joinToString("\n") { "👤 ${it.name} - 📞 ${it.phoneNumber}" }
            return HandlerResult.DirectReply("وجدت عدة جهات اتصال:\n$contactsInfo\n\nحدد من تريد.")
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
            } catch (e: Exception) { Log.e(TAG, "فشل الاتصال", e) }
        }
    }

    private suspend fun handleOpenBrowser(): HandlerResult {
        val browserApps = appResolver.getBrowserApps()
        if (browserApps.isEmpty()) return HandlerResult.Error("⚠️ لا يوجد متصفح مثبت.")
        appResolver.openApp(browserApps.first().packageName)
        return HandlerResult.DirectReply("🌐 تم فتح ${browserApps.first().appName}")
    }

    private suspend fun handleOpenCamera(): HandlerResult {
        return HandlerResult.DirectReply("📷 جاري فتح الكاميرا...").also {
            try {
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            } catch (e: Exception) { Log.e(TAG, "فشل فتح الكاميرا", e) }
        }
    }

    /**
     * رد ذكي للتعامل مع العدد الكبير من التطبيقات (257 تطبيقاً)
     */
    private suspend fun handleListApps(): HandlerResult {
        val allApps = appResolver.getAllInstalledApps()
        if (allApps.isEmpty()) return HandlerResult.Error("⚠️ لم أتمكن من جلب قائمة التطبيقات.")

        val totalCount = allApps.size
        
        // عرض عينة صغيرة مع توجيه المستخدم للاستخدام الأمثل
        val sampleApps = allApps.take(15).joinToString(" • ") { it.appName }
        
        return HandlerResult.DirectReply(
            "📱 لديك **$totalCount** تطبيقاً مثبتاً على هاتفك.\n\n" +
            "بسبب العدد الكبير، لا يمكنني سرد الكل دفعة واحدة لتجنب تعليق الشاشة.\n\n" +
            "💡 **كيف يمكنني مساعدتك؟**\n" +
            "1. قل: \"افتح تطبيق [الاسم]\" (مثال: افتح تطبيق Instagram)\n" +
            "2. اسأل: \"هل لدي تطبيق [الاسم]؟\"\n\n" +
            "👀 *عينة من تطبيقاتك:* $sampleApps ..."
        )
    }

    /**
     * فتح تطبيق محدد بالاسم (مطور للبحث في 257 تطبيقاً)
     */
    private suspend fun handleOpenApp(text: String): HandlerResult {
        val appName = extractAppName(text)
        
        if (appName.isNullOrBlank()) {
            return HandlerResult.Error("⚠️ لم أستطع تحديد اسم التطبيق. قل مثلاً: 'افتح تطبيق Instagram'")
        }

        val allApps = appResolver.getAllInstalledApps()
        
        // بحث ذكي: يتطابق مع اسم التطبيق أو اسم الحزمة (Package Name)
        val targetApp = allApps.find { app ->
            app.appName.contains(appName, ignoreCase = true) ||
            app.packageName.contains(appName, ignoreCase = true)
        }

        if (targetApp == null) {
            // محاولة البحث عن تطبيقات تحتوي على جزء من الاسم
            val similarApps = allApps.filter { 
                it.appName.contains(appName, ignoreCase = true) || 
                it.packageName.contains(appName, ignoreCase = true)
            }
            
            return if (similarApps.isNotEmpty()) {
                HandlerResult.Error("⚠️ لم أجد تطبيقاً باسم '$appName' بالضبط.\nهل تقصد أحد هذه التطبيقات:\n" + 
                    similarApps.take(5).joinToString("\n") { "• ${it.appName}" })
            } else {
                HandlerResult.Error("⚠️ لم أجد أي تطبيق على هاتفك يحتوي على الاسم '$appName'.")
            }
        }

        return try {
            appResolver.openApp(targetApp.packageName)
            HandlerResult.DirectReply("✅ جاري فتح تطبيق **${targetApp.appName}**...")
        } catch (e: Exception) {
            Log.e(TAG, "فشل فتح التطبيق", e)
            HandlerResult.Error("⚠️ فشل فتح تطبيق ${targetApp.appName}")
        }
    }

    private suspend fun handleSetAlarm(text: String): HandlerResult {
        val request = setAlarmHandler.parseAlarmRequest(text)
            ?: return HandlerResult.Error("⚠️ لم أفهم وقت المنبه.\nمثال: اضبط المنبه على الساعة 7 صباحاً")

        val timeStr = setAlarmHandler.formatTime(request.hour, request.minute)
        return if (setAlarmHandler.setAlarm(request)) HandlerResult.DirectReply("⏰ جاري ضبط المنبه على $timeStr")
        else HandlerResult.Error("⚠️ فشل ضبط المنبه")
    }

    fun executeChoice(app: AppResolver.InstalledApp, action: PendingAction): String {
        return when (action.type) {
            "MESSAGE" -> {
                appResolver.openApp(app.packageName)
                "✅ جاري فتح ${app.appName} لمراسلة:\n${action.contactDetails ?: ""}"
            }
            "MAP" -> {
                if (action.lat != null && action.lon != null) {
                    if (appResolver.openMapsWithCoords(app.packageName, action.lat, action.lon)) "🗺️ تم فتح ${app.appName}" else "⚠️ فشل فتح ${app.appName}"
                } else "⚠️ لا توجد إحداثيات"
            }
            else -> if (appResolver.openApp(app.packageName)) "✅ تم فتح ${app.appName}" else "⚠️ فشل فتح ${app.appName}"
        }
    }

    private fun extractAppName(text: String): String? {
        val patterns = listOf("تطبيق ", "تطبيق", "افتح ", "افتح", "افتحي ", "افتحي", "شغل ", "شغل", "هل لدي تطبيق ", "هل يوجد تطبيق ")
        for (pattern in patterns) {
            val idx = text.indexOf(pattern, ignoreCase = true)
            if (idx >= 0) {
                val rest = text.substring(idx + pattern.length).trim()
                val words = rest.split(" ")
                if (words.isNotEmpty()) {
                    val name = words.firstOrNull { it.isNotBlank() }
                    if (name != null && name.isNotBlank()) return name
                }
            }
        }
        return null
    }

    private fun extractName(text: String): String? {
        val patterns = listOf("لـ", "ل ", "بـ", "ب ", "الى ", "إلى ", "مع ", "إرسال رسالة إلى ", "ارسل رسالة لـ ")
        for (pattern in patterns) {
            val idx = text.indexOf(pattern, ignoreCase = true)
            if (idx >= 0) {
                var rest = text.substring(idx + pattern.length).trim()
                val stopWords = listOf("غدا", "الآن", "اليوم", "رجاء", "من فضلك", "please", "now", "today", "على")
                for (word in stopWords) {
                    rest = rest.replace(Regex("\\b$word\\b", RegexOption.IGNORE_CASE), "").trim()
                }
                if (rest.isNotBlank()) return rest
            }
        }
        return null
    }
}
