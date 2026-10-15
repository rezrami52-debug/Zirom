package com.racha.ai.core.apps

import android.content.Context
import android.util.Log
import com.racha.ai.core.agent.IntentMatcher
import com.racha.ai.domain.model.PrayerTimes
import com.racha.ai.services.location.LocationHelper
import com.racha.ai.services.prayer.PrayerTimesCalculator
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * يعالج أسئلة مواقيت الصلاة.
 */
class PrayerIntentHandler(private val context: Context) {

    companion object {
        private const val TAG = "RachaPrayerIntent"

        private val PRAYER_NAMES = mapOf(
            "فجر" to "الفجر",
            "الفجر" to "الفجر",
            "ظهر" to "الظهر",
            "الظهر" to "الظهر",
            "عصر" to "العصر",
            "العصر" to "العصر",
            "مغرب" to "المغرب",
            "المغرب" to "المغرب",
            "عشاء" to "العشاء",
            "العشاء" to "العشاء"
        )
    }

    /**
     * هل السؤال عن الصلاة؟
     */
    fun isPrayerQuestion(input: String): Boolean {
        val normalized = input.lowercase()
        return (normalized.contains("صلاه") || normalized.contains("صلاة") ||
                normalized.contains("فجر") || normalized.contains("ظهر") ||
                normalized.contains("عصر") || normalized.contains("مغرب") ||
                normalized.contains("عشاء")) &&
               (normalized.contains("كم") || normalized.contains("متى") ||
                normalized.contains("بقي") || normalized.contains("بقى") ||
                normalized.contains("وقت") || normalized.contains("موعد"))
    }

    /**
     * معالجة السؤال.
     */
    suspend fun handle(input: String): String {
        val times = calculatePrayerTimes()
            ?: return "⚠️ لا أستطيع حساب مواقيت الصلاة.\nتأكد من تفعيل GPS."

        val normalized = input.lowercase()

        // هل يذكر صلاة معينة؟
        for ((key, name) in PRAYER_NAMES) {
            if (normalized.contains(key)) {
                return answerForPrayer(name, times)
            }
        }

        // عام: كل المواقيت
        return allPrayers(times)
    }

    /**
     * إجابة لصلاة معينة.
     */
    private fun answerForPrayer(prayerName: String, times: PrayerTimes): String {
        val prayerTime = when (prayerName) {
            "الفجر" -> times.fajr
            "الظهر" -> times.dhuhr
            "العصر" -> times.asr
            "المغرب" -> times.maghrib
            "العشاء" -> times.isha
            else -> null
        } ?: return "⚠️ صلاة غير معروفة: $prayerName"

        val now = Date()
        val diff = prayerTime.time - now.time

        return if (diff > 0) {
            val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
            val hours = mins / 60
            val remainingMins = mins % 60

            val timeStr = formatTime(prayerTime)
            val remainingStr = when {
                hours > 0 -> "$hours ساعة و $remainingMins دقيقة"
                else -> "$mins دقيقة"
            }

            "🕌 صلاة $prayerName:\n" +
            "الوقت: $timeStr\n" +
            "باقي: $remainingStr"
        } else {
            val minsAgo = TimeUnit.MILLISECONDS.toMinutes(-diff)
            val timeStr = formatTime(prayerTime)
            "🕌 صلاة $prayerName كانت في: $timeStr\n" +
            "منذ $minsAgo دقيقة"
        }
    }

    /**
     * كل المواقيت.
     */
    private fun allPrayers(times: PrayerTimes): String {
        val now = Date()
        val next = findNextPrayer(times, now)

        val sb = StringBuilder()
        sb.append("🕌 مواقيت الصلاة اليوم:\n\n")
        sb.append("🌄 الفجر:    ${formatTime(times.fajr)}\n")
        sb.append("☀️ الظهر:    ${formatTime(times.dhuhr)}\n")
        sb.append("🌤️ العصر:    ${formatTime(times.asr)}\n")
        sb.append("🌆 المغرب:   ${formatTime(times.maghrib)}\n")
        sb.append("🌙 العشاء:   ${formatTime(times.isha)}\n")

        if (next != null) {
            val diff = next.second.time - now.time
            val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
            val hours = mins / 60
            val remainingMins = mins % 60

            val remainingStr = when {
                hours > 0 -> "$hours ساعة و $remainingMins دقيقة"
                else -> "$mins دقيقة"
            }

            sb.append("\n⏰ الصلاة القادمة: ${next.first}\n")
            sb.append("باقي: $remainingStr")
        }

        return sb.toString()
    }

    /**
     * حساب المواقيت.
     */
    private suspend fun calculatePrayerTimes(): PrayerTimes? {
        return try {
            val location = LocationHelper.getCurrentLocation(context) ?: return null
            PrayerTimesCalculator.calculate(
                latitude = location.latitude,
                longitude = location.longitude
            )
        } catch (e: Exception) {
            Log.e(TAG, "فشل حساب المواقيت", e)
            null
        }
    }

    /**
     * إيجاد الصلاة القادمة.
     */
    private fun findNextPrayer(times: PrayerTimes, now: Date): Pair<String, Date>? {
        val prayers = listOf(
            "الفجر" to times.fajr,
            "الظهر" to times.dhuhr,
            "العصر" to times.asr,
            "المغرب" to times.maghrib,
            "العشاء" to times.isha
        )
        return prayers.firstOrNull { it.second.after(now) }
    }

    /**
     * تنسيق الوقت.
     */
    private fun formatTime(date: Date): String {
        val cal = java.util.Calendar.getInstance()
        cal.time = date
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val m = cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
        return "$h:$m"
    }
}
