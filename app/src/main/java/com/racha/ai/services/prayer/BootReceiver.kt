package com.racha.ai.services.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.racha.ai.data.repository.RoutineRepository
import com.racha.ai.services.location.LocationHelper
import com.racha.ai.services.prayer.PrayerTimesCalculator
import com.racha.ai.services.routine.RoutineAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RachaBoot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "الهاتف أعيد تشغيله — إعادة الجدولة")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. إعادة جدولة إشعارات الصلاة
                val location = LocationHelper.getCurrentLocation(context)
                if (location != null) {
                    val times = PrayerTimesCalculator.calculate(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                    PrayerAlarmScheduler.scheduleAll(context, times)
                }

                // 2. إعادة جدولة الروتين
                val routineRepo = RoutineRepository(context)
                routineRepo.initializeDefaultIfEmpty()
                val items = routineRepo.getAll().firstOrNull() ?: emptyList()
                RoutineAlarmScheduler.scheduleAll(context, items)

            } catch (e: Exception) {
                Log.e(TAG, "فشل إعادة الجدولة", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
