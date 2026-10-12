package com.racha.ai.services.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.racha.ai.services.location.LocationHelper
import com.racha.ai.services.prayer.PrayerTimesCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * يعيد جدولة إشعارات الصلاة بعد إعادة تشغيل الهاتف.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RachaBoot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "الهاتف أعيد تشغيله — إعادة جدولة الصلاة")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val location = LocationHelper.getCurrentLocation(context)
                if (location != null) {
                    val times = PrayerTimesCalculator.calculate(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                    PrayerAlarmScheduler.scheduleAll(context, times)
                }
            } catch (e: Exception) {
                Log.e(TAG, "فشل إعادة الجدولة", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
