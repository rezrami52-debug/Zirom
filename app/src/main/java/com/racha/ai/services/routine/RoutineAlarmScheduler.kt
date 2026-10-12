package com.racha.ai.services.routine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.racha.ai.data.local.RoutineItem
import java.util.Calendar

object RoutineAlarmScheduler {

    private const val TAG = "RachaRoutine"
    const val EXTRA_ROUTINE_ID = "routine_id"
    const val EXTRA_ROUTINE_TITLE = "routine_title"
    const val EXTRA_ROUTINE_EMOJI = "routine_emoji"
    const val EXTRA_IS_PRE_ALERT = "is_pre_alert"

    private const val PRE_ALERT_MINUTES = 10

    fun scheduleAll(context: Context, items: List<RoutineItem>) {
        cancelAll(context)
        val enabled = items.filter { it.isEnabled }
        for (item in enabled) {
            scheduleItem(context, item)
        }
        Log.d(TAG, "تم جدولة ${enabled.size} عنصر روتين")
    }

    private fun scheduleItem(context: Context, item: RoutineItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val preId = item.id.hashCode() * 2
        val mainId = item.id.hashCode() * 2 + 1

        val preTime = calculateTrigger(item.hour, item.minute, -PRE_ALERT_MINUTES)
        if (preTime > System.currentTimeMillis()) {
            setAlarm(context, alarmManager, preTime, preId, item, isPreAlert = true)
        }

        val mainTime = calculateTrigger(item.hour, item.minute, 0)
        if (mainTime > System.currentTimeMillis()) {
            setAlarm(context, alarmManager, mainTime, mainId, item, isPreAlert = false)
        }
    }

    private fun calculateTrigger(hour: Int, minute: Int, offsetMinutes: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, offsetMinutes)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun setAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerAt: Long,
        requestId: Int,
        item: RoutineItem,
        isPreAlert: Boolean
    ) {
        val intent = Intent(context, RoutineAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ROUTINE_ID, item.id)
            putExtra(EXTRA_ROUTINE_TITLE, item.title)
            putExtra(EXTRA_ROUTINE_EMOJI, item.emoji)
            putExtra(EXTRA_IS_PRE_ALERT, isPreAlert)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, requestId, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Log.d(TAG, "جدولة: ${item.title} (pre=$isPreAlert) في ${java.util.Date(triggerAt)}")
        } catch (e: SecurityException) {
            Log.e(TAG, "فشل جدولة المنبه", e)
        }
    }

    fun cancelAll(context: Context) {
        Log.d(TAG, "إلغاء كل منبهات الروتين")
    }

    fun triggerTest(context: Context, item: RoutineItem, isPreAlert: Boolean) {
        RoutineAlarmReceiver.triggerManually(context, item, isPreAlert)
    }
}
