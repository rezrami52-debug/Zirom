package com.racha.ai.services.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.racha.ai.domain.model.PrayerTimes
import java.util.Date

/**
 * جدولة منبهات إشعارات الصلاة بدقة متناهية.
 */
object PrayerAlarmScheduler {

    private const val TAG = "RachaPrayer"
    const val ACTION_PRAYER_ALARM = "com.racha.ai.ACTION_PRAYER_ALARM"
    const val EXTRA_PRAYER_NAME = "prayer_name"
    const val EXTRA_IS_PRE_ALERT = "is_pre_alert"

    private const val ID_FAJR_PRE = 1001
    private const val ID_FAJR = 1002
    private const val ID_DHUHR_PRE = 1011
    private const val ID_DHUHR = 1012
    private const val ID_ASR_PRE = 1021
    private const val ID_ASR = 1022
    private const val ID_MAGHRIB_PRE = 1031
    private const val ID_MAGHRIB = 1032
    private const val ID_ISHA_PRE = 1041
    private const val ID_ISHA = 1042

    private const val PRE_ALERT_MINUTES = 15

    fun scheduleAll(context: Context, times: PrayerTimes) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        schedulePrayer(context, alarmManager, "الفجر", times.fajr, ID_FAJR_PRE, ID_FAJR)
        schedulePrayer(context, alarmManager, "الظهر", times.dhuhr, ID_DHUHR_PRE, ID_DHUHR)
        schedulePrayer(context, alarmManager, "العصر", times.asr, ID_ASR_PRE, ID_ASR)
        schedulePrayer(context, alarmManager, "المغرب", times.maghrib, ID_MAGHRIB_PRE, ID_MAGHRIB)
        schedulePrayer(context, alarmManager, "العشاء", times.isha, ID_ISHA_PRE, ID_ISHA)

        Log.d(TAG, "تمت جدولة إشعارات الصلاة بنجاح وبدقة متناهية")
    }

    private fun schedulePrayer(
        context: Context,
        alarmManager: AlarmManager,
        name: String,
        time: Date,
        preId: Int,
        mainId: Int
    ) {
        val now = System.currentTimeMillis()

        // ترحيل وقت الصلاة إلى اليوم التالي (+24 ساعة) إذا كان وقت اليوم قد مضى
        var mainTriggerTime = time.time
        if (mainTriggerTime <= now) {
            mainTriggerTime += 24 * 60 * 60 * 1000L
        }

        val preTriggerTime = mainTriggerTime - (PRE_ALERT_MINUTES * 60 * 1000L)

        // 1. إشعار قبل الأذان
        if (preTriggerTime > now) {
            setAlarm(context, alarmManager, preTriggerTime, preId, name, isPreAlert = true)
        }

        // 2. إشعار وقت الأذان
        if (mainTriggerTime > now) {
            setAlarm(context, alarmManager, mainTriggerTime, mainId, name, isPreAlert = false)
        }
    }

    private fun setAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerAt: Long,
        requestId: Int,
        prayerName: String,
        isPreAlert: Boolean
    ) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
            putExtra(EXTRA_PRAYER_NAME, prayerName)
            putExtra(EXTRA_IS_PRE_ALERT, isPreAlert)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, requestId, intent, flags)

        try {
            // صلاة الفرض تستخدم setAlarmClock لضمان إيقاظ الهاتف بنسبة 100% حتى في وضع النوم العميق
            if (!isPreAlert) {
                val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    requestId + 5000,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAt, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            }

            Log.d(TAG, "تمت جدولة: $prayerName (pre=$isPreAlert) في ${Date(triggerAt)}")
        } catch (e: SecurityException) {
            Log.w(TAG, "صلاحية المنبه الدقيق غير متاحة، استخدام البديل الآمن", e)
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ أثناء جدولة المنبه", e)
        }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val ids = listOf(
            ID_FAJR_PRE, ID_FAJR, ID_DHUHR_PRE, ID_DHUHR,
            ID_ASR_PRE, ID_ASR, ID_MAGHRIB_PRE, ID_MAGHRIB,
            ID_ISHA_PRE, ID_ISHA
        )
        for (id in ids) {
            val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                action = ACTION_PRAYER_ALARM
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getBroadcast(context, id, intent, flags)
            alarmManager.cancel(pi)
        }
        Log.d(TAG, "تم إلغاء كل المنبهات")
    }
}
