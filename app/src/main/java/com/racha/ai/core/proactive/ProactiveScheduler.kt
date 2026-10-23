package com.racha.ai.core.proactive

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ProactiveScheduler {
    private const val WORK_NAME = "JarvisProactiveNewsWork"

    fun scheduleNewsWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // يعمل فقط عند وجود اتصال إنترنت
            .build()

        val proactiveWorkRequest = PeriodicWorkRequestBuilder<ProactiveNewsWorker>(
            3, TimeUnit.HOURS,
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            proactiveWorkRequest
        )
    }
}
