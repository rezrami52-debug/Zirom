package com.racha.ai.services.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.racha.ai.domain.model.PrayerTimes
import java.util.Calendar
import java.util.Date

/**
 * جدولة منبهات إشعارات الصلاة.
 */
object PrayerAlarmScheduler {

    private const val TAG = "RachaPrayer"
    const val EXTRA_PRAYER_NAME = "prayer_name"
    const val EXTRA_IS_PRE_ALERT = "is_pre_alert"

    // معرفات المنبهات (كل صلاة لها معرّفان: قبل الأذان + وقت الأذان)
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

    // وقت الإشعار المسبق (بالدقائق)
    private const val PRE_ALERT_MINUTES = 15

    /**
     * جدولة كل الإشعارات لليوم الحالي.
     */
    fun scheduleAll(context: Context, times: PrayerTimes) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        schedulePrayer(context, alarmManager, "الفجر", times.fajr, ID_FAJR_PRE, ID_FAJR)
        schedulePrayer(context, alarmManager, "الظهر", times.dhuhr, ID_DHUHR_PRE, ID_DHUHR)
        schedulePrayer(context, alarmManager, "العصر", times.asr, ID_ASR_PRE, ID_ASR)
        schedulePrayer(context, alarmManager, "المغرب", times.maghrib, ID_MAGHRIB_PRE, ID_MAGHRIB)
        schedulePrayer(context, alarmManager, "العشاء", times.isha, ID_ISHA_PRE, ID_ISHA)

        Log.d(TAG, "تم جدولة إشعارات الصلاة")
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

        // 1. إشعار قبل الأذان
        val preTime = time.time - (PRE_ALERT_MINUTES * 60 * 1000L)
        if (preTime > now) {
            setAlarm(context, alarmManager, preTime, preId, name, isPreAlert = true)
        }

        // 2. إشعار وقت الأذان
        if (time.time > now) {
            setAlarm(context, alarmManager, time.time, mainId, name, isPreAlert = false)
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
            putExtra(EXTRA_PRAYER_NAME, prayerName)
            putExtra(EXTRA_IS_PRE_ALERT, isPreAlert)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, requestId, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                    )
                } else {
                    // Android 12+ بدون صلاحية Exact Alarm
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
                )
            }

            Log.d(TAG, "جدولة: $prayerName (pre=$isPreAlert) في ${java.util.Date(triggerAt)}")
        } catch (e: SecurityException) {
            Log.e(TAG, "فشل جدولة المنبه", e)
        }
    }

    /**
     * إلغاء كل المنبهات.
     */
    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val ids = listOf(
            ID_FAJR_PRE, ID_FAJR, ID_DHUHR_PRE, ID_DHUHR,
            ID_ASR_PRE, ID_ASR, ID_MAGHRIB_PRE, ID_MAGHRIB,
            ID_ISHA_PRE, ID_ISHA
        )
        for (id in ids) {
            val intent = Intent(context, PrayerAlarmReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getBroadcast(context, id, intent, flags)
            alarmManager.cancel(pi)
        }
        Log.d(TAG, "تم إلغاء كل المنبهات")
    }
}
