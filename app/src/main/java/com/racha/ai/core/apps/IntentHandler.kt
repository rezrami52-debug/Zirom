package com.racha.ai.core.apps

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.racha.ai.core.contacts.ContactResolver
import com.racha.ai.services.location.LocationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IntentHandler(
    private val context: Context,
    private val appResolver: AppResolver,
    private val contactResolver: ContactResolver
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
        return when (intentType) {
            // الأوامر المحلية الحياتية (تُنفذ فوراً بدون إنترنت)
            IntentClassifier.IntentType.GET_TIME -> handleGetTime()
            IntentClassifier.IntentType.GET_DATE -> handleGetDate()
            IntentClassifier.IntentType.GET_BATTERY -> handleGetBattery()
            IntentClassifier.IntentType.TOGGLE_FLASHLIGHT -> handleToggleFlashlight(text)
            IntentClassifier.IntentType.TOGGLE_BLUETOOTH -> handleToggleBluetooth()
            IntentClassifier.IntentType.GREETING -> handleGreeting()
            IntentClassifier.IntentType.SMALL_TALK -> handleSmallTalk(text)
            IntentClassifier.IntentType.PRAYER_TIMES -> handlePrayerTimes()

            // أوامر النظام والتطبيقات
            IntentClassifier.IntentType.SHOW_LOCATION -> handleShowLocation()
            IntentClassifier.IntentType.OPEN_MAP -> handleOpenMap()
            IntentClassifier.IntentType.SEND_MESSAGE -> handleSendMessage(text)
            IntentClassifier.IntentType.MAKE_CALL -> handleMakeCall(text)
            IntentClassifier.IntentType.OPEN_BROWSER -> handleOpenBrowser()
            IntentClassifier.IntentType.OPEN_CAMERA -> handleOpenCamera()
            IntentClassifier.IntentType.LIST_APPS -> handleListApps()
            IntentClassifier.IntentType.SET_ALARM -> handleSetAlarm(text)
            IntentClassifier.IntentType.TOGGLE_WIFI -> handleToggleWifi()
            IntentClassifier.IntentType.SET_RINGER -> handleSetRingerMode(text)
            IntentClassifier.IntentType.CORRECTION -> HandlerResult.NeedsCorrection(text)
            else -> HandlerResult.Unknown
        }
    }

    // --- معالجات الأوامر المحلية الفورية ---

    private fun handleGetTime(): HandlerResult {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val time = sdf.format(Date())
        return HandlerResult.DirectReply("سيدي، الساعة الآن هي $time ⌚")
    }

    private fun handleGetDate(): HandlerResult {
        val sdf = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar"))
        val date = sdf.format(Date())
        return HandlerResult.DirectReply("اليوم هو $date 📅 سيدي.")
    }

    private fun handleGetBattery(): HandlerResult {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        return if (level >= 0 && scale > 0) {
            val pct = (level * 100) / scale
            val chargingText = if (isCharging) "وهو قيد الشحن حالياً ⚡" else "وهو في وضع الاستهلاك الطبيعي."
            HandlerResult.DirectReply("مستوى البطارية هو $pct% سيدي، $chargingText")
        } else {
            HandlerResult.DirectReply("سيدي، لم أتمكن من قراءة مقياس البطارية بدقة.")
        }
    }

    private fun handleToggleFlashlight(text: String): HandlerResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return HandlerResult.Error("⚠️ لا يوجد فلاش متاح في الجهاز.")

            val lower = text.lowercase()
            val enable = !(lower.contains("اطفئ") || lower.contains("اطفي") || lower.contains("إطفاء") || lower.contains("اغلق") || lower.contains("اقفل") || lower.contains("off"))
            cameraManager.setTorchMode(cameraId, enable)
            if (enable) HandlerResult.DirectReply("🔦 تم تشغيل الكشاف سيدي.")
            else HandlerResult.DirectReply("🔦 تم إطفاء الكشاف سيدي.")
        } catch (e: Exception) {
            HandlerResult.Error("⚠️ تعذر التحكم في الفلاش: ${e.message}")
        }
    }

    private fun handleToggleBluetooth(): HandlerResult {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            HandlerResult.DirectReply("📶 فتحت لك إعدادات البلوتوث سيدي للتحكم به.")
        } catch (e: Exception) {
            HandlerResult.Error("⚠️ تعذر فتح إعدادات البلوتوث.")
        }
    }

    private fun handleGreeting(): HandlerResult {
        val greetings = listOf(
            "أهلاً بك سيدي! أنا EVE، جاهزة دائماً في خدمتك 🫧",
            "مرحباً سيدي! يسعدني التحدث معك، كيف يمكنني مساعدتك الآن؟",
            "أهلاً سيدي! كل الأنظمة تعمل بكفاءة تامة، ما هو طلبك التالي؟",
            "يا هلا سيدي! يومك سعيد، أنا معك متى شئت."
        )
        return HandlerResult.DirectReply(greetings.random())
    }

    private fun handleSmallTalk(text: String): HandlerResult {
        val lower = text.lowercase()
        return when {
            lower.contains("كيف") || lower.contains("شلونك") || lower.contains("شنحوالك") -> {
                HandlerResult.DirectReply("أنا في أتم الجاهزية والنشاط سيدي! شكراً لسؤالك، كيف يمكنني تسهيل يومك؟")
            }
            lower.contains("من انت") || lower.contains("من أنت") || lower.contains("عرف") || lower.contains("مين") -> {
                HandlerResult.DirectReply("أنا EVE و Racha AI، مساعدك الشخصي والتكتيكي المتكامل، مستوحاة من روعة EVE وذكاء JARVIS.")
            }
            lower.contains("شكر") || lower.contains("عافية") -> {
                HandlerResult.DirectReply("العفو دائماً سيدي! أنا هنا في أي وقت تحتاجه.")
            }
            else -> {
                HandlerResult.DirectReply("سيدي، أنا أستمع إليك باهتمام، تفضل بأمرك.")
            }
        }
    }

    private fun handlePrayerTimes(): HandlerResult {
        return HandlerResult.DirectReply("🕌 سيدي، يمكنك الاطلاع على مواقيت الصلاة والقبلة بدقة عبر التبويب المخصص في الأسفل أو البطاقة العلوية.")
    }

    // --- معالجات أوامر النظام والتطبيقات السابقة ---

    private suspend fun handleShowLocation(): HandlerResult {
        val location = LocationHelper.getCurrentLocation(context) ?: return HandlerResult.Error("⚠️ لا أستطيع الوصول لموقعك. تأكد من تفعيل GPS.")
        val city = location.cityName ?: "منطقتك"
        return HandlerResult.DirectReply("📍 أنت في: $city\nالإحداثيات: (${location.latitude}, ${location.longitude})")
    }

    private suspend fun handleOpenMap(): HandlerResult {
        val location = LocationHelper.getCurrentLocation(context) ?: return HandlerResult.Error("⚠️ لا أستطيع الوصول لموقعك.")
        val mapApps = appResolver.getMapApps()
        if (mapApps.isEmpty()) return HandlerResult.Error("⚠️ لا يوجد تطبيق خرائط مثبت.")
        val preferredApp = mapApps.find { it.packageName == "com.google.android.apps.maps" || it.packageName == "com.google.earth" } ?: mapApps.first()
        val success = appResolver.openMapsWithCoords(preferredApp.packageName, location.latitude, location.longitude)
        return if (success) HandlerResult.DirectReply("🗺️ تم فتح ${preferredApp.appName}") else HandlerResult.Error("⚠️ فشل فتح الخريطة")
    }

    private suspend fun handleSendMessage(text: String): HandlerResult {
        val contactName = extractName(text) ?: "محدد"
        val contacts = contactResolver.searchContact(contactName)
        if (contacts.isEmpty()) return HandlerResult.Error("⚠️ لم أجد جهة اتصال باسم '$contactName'.")
        val contactsInfo = contacts.joinToString("\n") { "👤 ${it.name}\n📞 ${it.phoneNumber}" }
        val messagingApps = appResolver.getMessagingApps()
        if (messagingApps.isEmpty()) return HandlerResult.Error("⚠️ لا يوجد تطبيق مراسلة مثبت.\n\n$contactsInfo")
        if (messagingApps.size == 1) {
            appResolver.openApp(messagingApps[0].packageName)
            return HandlerResult.DirectReply("✅ وجدت:\n$contactsInfo\n\nتم فتح ${messagingApps[0].appName}")
        }
        return HandlerResult.ShowChooser(
            title = "💬 اختر تطبيق المراسلة لـ '$contactName'",
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
            } catch (e: Exception) {
                Log.e(TAG, "فشل الاتصال", e)
            }
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
        if (allApps.isEmpty()) return HandlerResult.Error("⚠️ لم أتمكن من جلب قائمة التطبيقات.")
        val previewApps = allApps.take(30)
        val appsList = previewApps.joinToString("\n") { "• ${it.appName}" }
        return HandlerResult.DirectReply("📱 لديك ${allApps.size} تطبيقاً مثبتاً. أهمها:\n$appsList")
    }

    private suspend fun handleSetAlarm(text: String): HandlerResult {
        val result = setAlarmHandler.handle(text)
        return HandlerResult.DirectReply(result)
    }

    private suspend fun handleToggleWifi(): HandlerResult {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            HandlerResult.DirectReply("📶 فتحت لك إعدادات الواي فاي سيدي للتحكم بها.")
        } catch (e: Exception) {
            HandlerResult.Error("⚠️ تعذر فتح إعدادات الواي فاي.")
        }
    }

    private suspend fun handleSetRingerMode(text: String): HandlerResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val lowerText = text.lowercase()
        return try {
            when {
                lowerText.contains("صامت") || lowerText.contains("silent") -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    HandlerResult.DirectReply("🔕 تم تحويل الهاتف إلى وضع الصامت.")
                }
                lowerText.contains("اهتزاز") || lowerText.contains("vibrate") -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    HandlerResult.DirectReply("📳 تم تحويل الهاتف إلى وضع الاهتزاز.")
                }
                lowerText.contains("عادي") || lowerText.contains("normal") || lowerText.contains("رنين") -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    HandlerResult.DirectReply("🔔 تم تحويل الهاتف إلى الوضع العادي.")
                }
                else -> HandlerResult.Error("⚠️ لم أفهم وضع الصوت المطلوب. جرب: صامت، اهتزاز، عادي.")
            }
        } catch (e: Exception) {
            HandlerResult.Error("⚠️ فشل تغيير وضع الصوت. تأكد من منح صلاحية 'عدم الإزعاج' (Do Not Disturb).")
        }
    }

    private fun extractName(text: String): String? {
        val patterns = listOf("لـ", "ل ", "بـ", "ب ", "الى ", "إلى ", "مع ")
        for (pattern in patterns) {
            val idx = text.indexOf(pattern)
            if (idx >= 0) {
                val rest = text.substring(idx + pattern.length).trim()
                if (rest.isNotBlank()) return rest.split(" ").firstOrNull { it.isNotBlank() }
            }
        }
        return null
    }
}
