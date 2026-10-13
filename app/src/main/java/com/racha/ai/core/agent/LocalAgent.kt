package com.racha.ai.core.agent

import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * معالج الأوامر المحلية.
 * ينفذ الأوامر البسيطة بدون internet.
 * الأوامر المعقدة → NeedsLLM.
 */
class LocalAgent(private val context: Context) {

    companion object {
        private const val TAG = "RachaLocalAgent"
    }

    /**
     * معالجة أمر.
     */
    fun processCommand(input: String): CommandResult {
        val normalized = IntentMatcher.normalize(input)

        Log.d(TAG, "معالجة: '$input' → '$normalized'")

        // ===== الصوت =====

        // إلغاء الصامت (يجب أن يأتي قبل "صامت")
        if (IntentMatcher.isNegation(input) &&
            IntentMatcher.containsAny(input, "صامت", "سكوت", "صمت", "silent")) {
            return executeSilent(false)
        }

        // الوضع الصامت
        if (IntentMatcher.containsAny(input, "صامت", "سكوت", "صمت", "silent") &&
            !IntentMatcher.isQuestion(input)) {
            return executeSilent(true)
        }

        // الاهتزاز
        if (IntentMatcher.containsAny(input, "اهتزاز", "vibrate") &&
            !IntentMatcher.isQuestion(input)) {
            return executeVibrate()
        }

        // الوضع العادي (مع صوت)
        if (IntentMatcher.containsAny(input, "عادي", "صوت", "رنين", "normal") &&
            IntentMatcher.isNegation(input).not() &&
            !IntentMatcher.isQuestion(input)) {
            return executeNormal()
        }

        // ===== الكاميرا =====

        if (IntentMatcher.containsAny(input, "كاميرا", "تصوير", "صور", "photo", "camera") &&
            !IntentMatcher.isQuestion(input)) {
            return CommandResult.NeedsAction(
                action = "open_camera",
                message = "📷 جاري فتح الكاميرا..."
            )
        }

        // ===== الوقت =====

        if (IntentMatcher.containsAny(input, "وقت", "ساعه", "time") &&
            IntentMatcher.isQuestion(input)) {
            return getCurrentTime()
        }

        // ===== البطارية =====

        if (IntentMatcher.containsAny(input, "بطاريه", "شحن", "battery") &&
            IntentMatcher.isQuestion(input)) {
            return getBatteryStatus()
        }

        // ===== لم يُفهم → LLM =====

        return CommandResult.NeedsLLM(input)
    }

    /**
     * الوضع الصامت / العادي.
     */
    private fun executeSilent(silent: Boolean): CommandResult {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = if (silent)
                AudioManager.RINGER_MODE_SILENT
            else
                AudioManager.RINGER_MODE_NORMAL

            CommandResult.Executed(
                message = if (silent)
                    "✅ تم تفعيل الوضع الصامت"
                else
                    "🔔 تم تفعيل الوضع العادي"
            )
        } catch (e: Exception) {
            Log.e(TAG, "فشل الصوت", e)
            CommandResult.Failed("⚠️ فشل: ${e.message}")
        }
    }

    /**
     * الوضع العادي.
     */
    private fun executeNormal(): CommandResult {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            CommandResult.Executed("🔔 تم تفعيل الوضع العادي")
        } catch (e: Exception) {
            CommandResult.Failed("⚠️ فشل: ${e.message}")
        }
    }

    /**
     * الاهتزاز.
     */
    private fun executeVibrate(): CommandResult {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            CommandResult.Executed("📳 تم تفعيل الاهتزاز")
        } catch (e: Exception) {
            CommandResult.Failed("⚠️ فشل: ${e.message}")
        }
    }

    /**
     * الوقت الحالي.
     */
    private fun getCurrentTime(): CommandResult {
        return try {
            val cal = java.util.Calendar.getInstance()
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = cal.get(java.util.Calendar.MINUTE)
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

    /**
     * حالة البطارية.
     */
    private fun getBatteryStatus(): CommandResult {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)

            val emoji = when {
                level >= 80 -> "🔋"
                level >= 50 -> "🔋"
                level >= 20 -> "🪫"
                else -> "🪫"
            }

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
