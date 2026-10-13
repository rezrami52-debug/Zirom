package com.racha.ai.services.sleep

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * جدولة حارس النوم — كل 30 دقيقة.
 */
object SleepGuardianScheduler {

    fun schedule(context: Context) {
        val constraints = Constraints.Builder().build()

        val request = PeriodicWorkRequestBuilder<SleepGuardianWorker>(
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SleepGuardianWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SleepGuardianWorker.WORK_NAME)
    }
}
