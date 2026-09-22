package com.nahrahviing.lecteurnovel.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nahrahviing.lecteurnovel.worker.ChapterUpdateWorker
import java.util.UUID
import java.util.concurrent.TimeUnit

object ChapterUpdateManager {

    fun scheduleDailyUpdate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Exécution quotidienne (24 heures)
        val periodicRequest = PeriodicWorkRequestBuilder<ChapterUpdateWorker>(
            24, TimeUnit.HOURS,
            2, TimeUnit.HOURS // flex interval
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ChapterUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }

    fun cancelDailyUpdate(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(ChapterUpdateWorker.WORK_NAME)
    }

    fun checkNow(context: Context): UUID {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeRequest = OneTimeWorkRequestBuilder<ChapterUpdateWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ChapterUpdateWorker.MANUAL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )

        return oneTimeRequest.id
    }
}
