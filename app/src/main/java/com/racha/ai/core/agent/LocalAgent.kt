package com.racha.ai.core.agent

import android.content.Context
import android.media.AudioManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class LocalAgent(private val context: Context) {

    companion object {
        private const val TAG = "RachaLocalAgent"
    }

    fun processCommand(input: String): CommandResult {
        Log.d(TAG, "معالجة: '$input'")

        // ===== الطقس (يحتاج LLM) =====
        // نضعها أولاً حتى لا يلتقطها "التاريخ"
        if (IntentMatcher.containsAny(input, "طقس", "الطقس", "weather")) {
            return CommandResult.NeedsLLM(input)
        }

        // ===== الصوت =====
        if (IntentMatcher.isNegation(input) &&
            IntentMatcher.containsAny(input, "صامت", "سكوت", "صمت", "silent")) {
            return executeSilent(false)
        }

        if (IntentMatcher.containsAny(input, "صامت", "سكوت", "صمت", "silent") &&
            !IntentMatcher.isQuestion(input)) {
            return executeSilent(true)
        }

        if (IntentMatcher.containsAny(input, "اهتزاز", "vibrate") &&
            !IntentMatcher.isQuestion(input)) {
            return executeVibrate()
        }

        if (IntentMatcher.containsAny(input, "عادي", "رنين", "normal") &&
            !IntentMatcher.isNegation(input) &&
            !IntentMatcher.isQuestion(input)) {
            return executeNormal()
        }

        // ===== الكاميرا =====
        if (IntentMatcher.containsAny(input, "كاميرا", "تصوير", "التقط", "صورة", "صور", "photo", "camera") &&
            !IntentMatcher.isQuestion(input)) {
            return CommandResult.NeedsAction(
                action = "open_camera",
                message = "📷 جاري فتح الكاميرا..."
            )
        }

        // ===== الوقت =====
        if (IntentMatcher.containsAny(input, "وقت", "ساعه", "الساعه", "time")) {
            return getCurrentTime()
        }

        // ===== التاريخ (مع استثناء الطقس) =====
        if (IntentMatcher.containsAny(input, "تاريخ", "التاريخ", "date")) {
            return getCurrentDate()
        }

        // ===== البطارية =====
        if (IntentMatcher.containsAny(input, "بطاريه", "شحن", "battery")) {
            return getBatteryStatus()
        }

        // ===== من أنت =====
        if (IntentMatcher.containsAny(input, "من انت", "من انا", "ما انت")) {
            return CommandResult.Executed(
                "أنا Racha، رفيقك الشخصي الذكي. أنا هنا لأساعدك في يومك. 💙"
            )
        }

        // ===== شكراً =====
        if (IntentMatcher.containsAny(input, "شكرا", "مشكور", "thanks")) {
            return CommandResult.Executed("العفو! أنا هنا دائماً. 🌟")
        }

        // ===== تحية =====
        if (IntentMatcher.containsAny(input, "مرحبا", "السلام", "اهلا", "hi", "hello")) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greeting = when (hour) {
                in 5..11 -> "صباح الخير! ☀️"
                in 12..16 -> "نهارك سعيد! 🌤️"
                in 17..20 -> "مساء الخير! 🌆"
                else -> "طابت ليلتك! 🌙"
            }
            return CommandResult.Executed("$greeting كيف حالك اليوم؟")
        }

        return CommandResult.NeedsLLM(input)
    }

    private fun executeSilent(silent: Boolean): CommandResult {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = if (silent)
                AudioManager.RINGER_MODE_SILENT
            else
                AudioManager.RINGER_MODE_NORMAL
            CommandResult.Executed(
                if (silent) "✅ تم تفعيل الوضع الصامت"
                else "🔔 تم تفعيل الوضع العادي"
            )
        } catch (e: Exception) {
            CommandResult.Failed("⚠️ فشل: ${e.message}")
        }
    }

    private fun executeNormal(): CommandResult {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            CommandResult.Executed("🔔 تم تفعيل الوضع العادي")
        } catch (e: Exception) {
            CommandResult.Failed("⚠️ فشل: ${e.message}")
        }
    }

    private fun executeVibrate(): CommandResult {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            CommandResult.Executed("📳 تم تفعيل الاهتزاز")
        } catch (e: Exception) {
            CommandResult.Failed("⚠️ فشل: ${e.message}")
        }
    }

    private fun getCurrentTime(): CommandResult {
        return try {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val h = hour.toString().padStart(2, '0')
            val m = minute.toString().padStart(2, '0')
            val greeting = when (hour) {
                in 5..11 -> "صباح الخير"
                in 12..16 -> "نهارك سعيد"
                in 17..20 -> "مساء الخير"
                else -> "طابت ليلتك"
            }
            CommandResult.Executed("🕐 $greeting. الساعة الآن $h:$m")
        } catch (e: Exception) {
            CommandResult.Failed("⚠️ فشل: ${e.message}")
        }
    }

    private fun getCurrentDate(): CommandResult {
        return try {
            val dateFormat = SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar"))
            val date = dateFormat.format(Date())
            CommandResult.Executed("📅 اليوم: $date")
        } catch (e: Exception) {
            CommandResult.Failed("⚠️ فشل: ${e.message}")
        }
    }

    private fun getBatteryStatus(): CommandResult {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val emoji = if (level >= 50) "🔋" else "🪫"
            val message = when {
                level >= 80 -> "$emoji البطارية ممتلئة تقريباً ($level%)"
                level >= 50 -> "$emoji البطارية جيدة ($level%)"
                level >= 20 -> "$emoji البطارية متوسطة ($level%)"
                else -> "$emoji البطارية منخفضة ($level%) — اشحن قريباً"
            }
            CommandResult.Executed(message)
        } catch (e: Exception) {
            CommandResult.Failed("⚠️ فشل: ${e.message}")
        }
    }
}
