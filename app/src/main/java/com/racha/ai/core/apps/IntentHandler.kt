package com.racha.ai.core.apps

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.racha.ai.core.contacts.ContactResolver
import com.racha.ai.core.eve.EveAccessibilityService
import com.racha.ai.core.eve.EveState
import com.racha.ai.core.eve.MediaShelfType
import com.racha.ai.core.shizuku.ShizukuShellRunner
import com.racha.ai.services.location.LocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
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
            IntentClassifier.IntentType.OPEN_PHOTOS -> {
                EveState.openShelf(MediaShelfType.PHOTOS)
                HandlerResult.DirectReply("🖼️ تفضل سيدي، تم فتح ألبوم الصور.")
            }
            IntentClassifier.IntentType.OPEN_VIDEOS -> {
                EveState.openShelf(MediaShelfType.VIDEOS)
                HandlerResult.DirectReply("📽️ تفضل سيدي، تم فتح مقاطع الفيديو.")
            }
            IntentClassifier.IntentType.OPEN_MUSIC -> {
                EveState.openShelf(MediaShelfType.MUSIC)
                HandlerResult.DirectReply("🎶 تفضل سيدي، تم فتح قائمة الموسيقى.")
            }
            IntentClassifier.IntentType.FIND_MY_PHONE -> handleFindMyPhone()
            IntentClassifier.IntentType.MOSQUE_MODE -> handleMosqueMode()
            IntentClassifier.IntentType.MACRO_SLEEP -> handleMacroSleep()
            IntentClassifier.IntentType.MACRO_CINEMA -> handleMacroCinema()
            IntentClassifier.IntentType.MACRO_WORK -> handleMacroWork()
            IntentClassifier.IntentType.GET_TIME -> handleGetTime()
            IntentClassifier.IntentType.GET_DATE -> handleGetDate()
            IntentClassifier.IntentType.GET_BATTERY -> handleGetBattery()
            IntentClassifier.IntentType.TOGGLE_FLASHLIGHT -> handleToggleFlashlight(text)
            IntentClassifier.IntentType.TOGGLE_BLUETOOTH -> handleToggleBluetooth(text)
            IntentClassifier.IntentType.TOGGLE_WIFI -> handleToggleWifi(text)
            IntentClassifier.IntentType.SET_RINGER -> handleSetRingerMode(text)
            IntentClassifier.IntentType.GREETING -> handleGreeting()
            IntentClassifier.IntentType.SMALL_TALK -> handleSmallTalk(text)
            IntentClassifier.IntentType.PRAYER_TIMES -> handlePrayerTimes()
            IntentClassifier.IntentType.SHOW_LOCATION -> handleShowLocation()
            IntentClassifier.IntentType.OPEN_MAP -> handleOpenMap()
            IntentClassifier.IntentType.SEND_MESSAGE -> handleSendMessage(text)
            IntentClassifier.IntentType.MAKE_CALL -> handleMakeCall(text)
            IntentClassifier.IntentType.OPEN_BROWSER -> handleOpenBrowser()
            IntentClassifier.IntentType.OPEN_CAMERA -> handleOpenCamera()
            IntentClassifier.IntentType.LIST_APPS -> handleListApps()
            IntentClassifier.IntentType.SET_ALARM -> handleSetAlarm(text)
            IntentClassifier.IntentType.CORRECTION -> HandlerResult.NeedsCorrection(text)
            else -> handleAdvancedCommands(text)
        }
    }

    private suspend fun handleSendMessage(text: String): HandlerResult {
        val contactName = extractName(text) ?: "محدد"
        val messageBody = extractMessageContent(text)
        val contacts = contactResolver.searchContact(contactName)

        if (contacts.isEmpty()) return HandlerResult.Error("⚠️ لم أجد جهة اتصال باسم '$contactName'.")
        val targetContact = contacts.first()

        val cleanPhone = targetContact.phoneNumber.replace(Regex("[^0-9+]"), "")

        // إرسال واتساب المؤتمت بالكامل
        return try {
            val encodedMsg = URLEncoder.encode(messageBody, "UTF-8")
            val whatsappUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMsg")
            val intent = Intent(Intent.ACTION_VIEW, whatsappUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.whatsapp")
            }
            context.startActivity(intent)

            // إعطاء أمر لـ Accessibility للضغط على زر الإرسال تلقائياً
            EveAccessibilityService.instance?.clickWhatsAppSendButton()

            HandlerResult.DirectReply("✅ جاري إرسال رسالتك إلى ${targetContact.name} على واتساب تلقائياً.")
        } catch (e: Exception) {
            HandlerResult.DirectReply("✅ فتحت محادثة ${targetContact.name} لإرسال رسالتك.")
        }
    }

    private fun extractMessageContent(text: String): String {
        val keywords = listOf("قلو", "قل له", "أخبره", "رسالة مفادها", "محتواها", "نصها", "بأن", "أن ")
        for (kw in keywords) {
            val idx = text.indexOf(kw)
            if (idx >= 0) {
                return text.substring(idx + kw.length).trim()
            }
        }
        return "مرحباً، أرسل لك هذه الرسالة عبر مساعد Racha الذكي."
    }

    private suspend fun handleFindMyPhone(): HandlerResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
        audioManager.setStreamVolume(AudioManager.STREAM_RING, audioManager.getStreamMaxVolume(AudioManager.STREAM_RING), 0)

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    repeat(6) {
                        cameraManager.setTorchMode(cameraId, true)
                        delay(200)
                        cameraManager.setTorchMode(cameraId, false)
                        delay(200)
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return HandlerResult.DirectReply("📢 أنا هنا سيدي! أنا هنا بجانبك!")
    }

    private fun handleMosqueMode(): HandlerResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        CoroutineScope(Dispatchers.IO).launch {
            delay(25 * 60 * 1000L)
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        }
        return HandlerResult.DirectReply("🕌 تم تفعيل وضع المسجد سيدي، الهاتف في وضع الصامت لمدة 25 دقيقة وسيعود تلقائياً بعدها.")
    }

    private fun handleMacroSleep(): HandlerResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        ShizukuShellRunner.setWifiEnabled(false)
        ShizukuShellRunner.setBluetoothEnabled(false)
        CoroutineScope(Dispatchers.IO).launch {
            delay(2000)
            ShizukuShellRunner.lockScreen()
        }
        return HandlerResult.DirectReply("🌙 تم تفعيل وضع النوم سيدي، تم إغلاق الاتصالات ووضع الهاتف على الصامت. أحلاماً سعيدة.")
    }

    private fun handleMacroCinema(): HandlerResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        return HandlerResult.DirectReply("🍿 تم تفعيل وضع السينما سيدي، الهاتف في وضع الصامت. مشاهدة ممتعة.")
    }

    private fun handleMacroWork(): HandlerResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        return HandlerResult.DirectReply("💼 تم تفعيل وضع التركيز سيدي، الهاتف في وضع الاهتزاز.")
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
                if (appResolver.openApp(app.packageName)) "✅ تم فتح ${app.appName}" else "⚠️ فشل فتح ${app.appName}"
            }
        }
    }

    private fun handleAdvancedCommands(text: String): HandlerResult {
        val lower = text.lowercase()
        return when {
            lower.contains("ردي") || lower.contains("جاوبي") || lower.contains("افتحي الخط") -> {
                ShizukuShellRunner.execCommand("input keyevent 79")
                HandlerResult.DirectReply("تم فتح المكالمة سيدي.")
            }
            lower.contains("ارفضي") || lower.contains("اقفلي الخط") || lower.contains("انهي المكالمة") -> {
                ShizukuShellRunner.execCommand("input keyevent 6")
                HandlerResult.DirectReply("تم رفض المكالمة سيدي.")
            }
            lower.contains("اقفل الشاشة") || lower.contains("قفل الشاشة") || lower.contains("اطفي الشاشة") -> {
                ShizukuShellRunner.lockScreen()
                HandlerResult.DirectReply("سيدي، تم إغلاق الشاشة.")
            }
            lower.contains("صورة للشاشة") || lower.contains("سكرين شوت") || lower.contains("لقطة شاشة") -> {
                ShizukuShellRunner.takeScreenshot()
                HandlerResult.DirectReply("تم التقاط صورة الشاشة وحفظها بنجاح سيدي.")
            }
            lower.contains("ماذا ترى") || lower.contains("اقرأ الشاشة") -> {
                val snapshot = EveAccessibilityService.instance?.getLatestScreenSnapshot()
                if (snapshot != null && snapshot.visibleText.isNotBlank()) {
                    HandlerResult.DirectReply("أرى تطبيق ${snapshot.appName}:\n" + snapshot.visibleText.take(180))
                } else {
                    HandlerResult.DirectReply("سيدي، لا أستطيع قراءة الشاشة حالياً. تأكد من تفعيل خدمة إمكانية الوصول.")
                }
            }
            else -> HandlerResult.Unknown
        }
    }

    private fun handleGetTime(): HandlerResult {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return HandlerResult.DirectReply("سيدي، الساعة الآن هي ${sdf.format(Date())} ⌚")
    }

    private fun handleGetDate(): HandlerResult {
        val sdf = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar"))
        return HandlerResult.DirectReply("اليوم هو ${sdf.format(Date())} 📅 سيدي.")
    }

    private fun handleGetBattery(): HandlerResult {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            HandlerResult.DirectReply("مستوى البطارية هو ${(level * 100) / scale}% سيدي ⚡")
        } else HandlerResult.DirectReply("سيدي، تعذر قراءة مقياس البطارية.")
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

    private fun handleToggleWifi(text: String): HandlerResult {
        val lower = text.lowercase()
        val enable = !(lower.contains("اطفئ") || lower.contains("اطفي") || lower.contains("إطفاء") || lower.contains("اغلق") || lower.contains("اقفل") || lower.contains("off"))
        val success = ShizukuShellRunner.setWifiEnabled(enable)
        return if (success) {
            HandlerResult.DirectReply(if (enable) "📶 تم تشغيل الواي فاي مباشرة سيدي." else "📶 تم إيقاف الواي فاي مباشرة سيدي.")
        } else {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            HandlerResult.DirectReply("📶 فتحت لك إعدادات الواي فاي سيدي.")
        }
    }

    private fun handleToggleBluetooth(text: String): HandlerResult {
        val lower = text.lowercase()
        val enable = !(lower.contains("اطفئ") || lower.contains("اطفي") || lower.contains("إطفاء") || lower.contains("off"))
        val success = ShizukuShellRunner.setBluetoothEnabled(enable)
        return if (success) {
            HandlerResult.DirectReply(if (enable) "📶 تم تشغيل البلوتوث سيدي." else "📶 تم إيقاف البلوتوث سيدي.")
        } else {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            HandlerResult.DirectReply("📶 فتحت لك إعدادات البلوتوث سيدي.")
        }
    }

    private fun handleSetRingerMode(text: String): HandlerResult {
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
                else -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    HandlerResult.DirectReply("🔔 تم تحويل الهاتف إلى الوضع العادي.")
                }
            }
        } catch (e: Exception) {
            HandlerResult.Error("⚠️ فشل تغيير وضع الصوت.")
        }
    }

    private fun handleGreeting(): HandlerResult {
        return HandlerResult.DirectReply("أهلاً بك سيدي! أنا EVE، جاهزة دائماً في خدمتك 🫧")
    }

    private fun handleSmallTalk(text: String): HandlerResult {
        val lower = text.lowercase()
        return when {
            lower.contains("كيف") || lower.contains("شلونك") || lower.contains("how are you") -> {
                HandlerResult.DirectReply("أنا في أتم الجاهزية والنشاط سيدي! شكراً لسؤالك، كيف يمكنني تسهيل يومك؟")
            }
            lower.contains("من انت") || lower.contains("عرف") || lower.contains("هل انت") -> {
                HandlerResult.DirectReply("أنا EVE و Racha AI، مساعدك الشخصي والتكتيكي المتكامل.")
            }
            else -> HandlerResult.DirectReply("العفو دائماً سيدي! أنا هنا في أي وقت تحتاجه.")
        }
    }

    private fun handlePrayerTimes(): HandlerResult = HandlerResult.DirectReply("🕌 سيدي، مواقيت الصلاة والقبلة متاحة في التبويب بالأسفل.")

    private suspend fun handleShowLocation(): HandlerResult {
        val location = LocationHelper.getCurrentLocation(context) ?: return HandlerResult.Error("⚠️ لا أستطيع الوصول لموقعك.")
        return HandlerResult.DirectReply("📍 أنت في: ${location.cityName ?: "منطقتك"}")
    }

    private suspend fun handleOpenMap(): HandlerResult {
        val location = LocationHelper.getCurrentLocation(context) ?: return HandlerResult.Error("⚠️ لا أستطيع الوصول لموقعك.")
        val mapApps = appResolver.getMapApps()
        if (mapApps.isEmpty()) return HandlerResult.Error("⚠️ لا يوجد تطبيق خرائط مثبت.")
        appResolver.openMapsWithCoords(mapApps.first().packageName, location.latitude, location.longitude)
        return HandlerResult.DirectReply("🗺️ تم فتح الخريطة سيدي.")
    }

    private suspend fun handleMakeCall(text: String): HandlerResult {
        val contactName = extractName(text)
        val phoneRegex = Regex("\\d{8,}")
        val phoneNumber = phoneRegex.find(text)?.value
        if (phoneNumber != null) return makePhoneCall(phoneNumber)
        if (contactName != null) {
            val contacts = contactResolver.searchContact(contactName)
            if (contacts.isEmpty()) return HandlerResult.Error("⚠️ لم أجد جهة اتصال باسم '$contactName'.")
            return makePhoneCall(contacts.first().phoneNumber)
        }
        return HandlerResult.Error("⚠️ حدد اسم جهة الاتصال أو الرقم.")
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
        return HandlerResult.DirectReply("🌐 تم فتح المتصفح سيدي.")
    }

    private suspend fun handleOpenCamera(): HandlerResult {
        return HandlerResult.DirectReply("📷 تم فتح الكاميرا سيدي.").also {
            try {
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            } catch (e: Exception) { Log.e(TAG, "فشل فتح الكاميرا", e) }
        }
    }

    private suspend fun handleListApps(): HandlerResult {
        val allApps = appResolver.getAllInstalledApps()
        return HandlerResult.DirectReply("📱 لديك ${allApps.size} تطبيق مثبت في هاتفك سيدي.")
    }

    private suspend fun handleSetAlarm(text: String): HandlerResult {
        val request = setAlarmHandler.parseAlarmRequest(text) ?: return HandlerResult.Error("⚠️ لم أفهم وقت المنبه بدقة.")
        val timeStr = setAlarmHandler.formatTime(request.hour, request.minute)
        return if (setAlarmHandler.setAlarm(request)) HandlerResult.DirectReply("⏰ تم ضبط المنبه على الساعة $timeStr سيدي.")
        else HandlerResult.Error("⚠️ فشل ضبط المنبه")
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
