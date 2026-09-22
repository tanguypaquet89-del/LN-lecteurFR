package com.nahrahviing.lecteurnovel.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nahrahviing.lecteurnovel.worker.AutoDriveBackupWorker
import java.util.concurrent.TimeUnit

object AutoDriveBackupManager {

    fun schedulePeriodicBackup(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Exécution périodique toutes les 12 heures
        val periodicRequest = PeriodicWorkRequestBuilder<AutoDriveBackupWorker>(
            12, TimeUnit.HOURS,
            1, TimeUnit.HOURS // Flex interval
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AutoDriveBackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }

    fun cancelPeriodicBackup(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(AutoDriveBackupWorker.WORK_NAME)
    }

    fun triggerImmediateBackup(context: Context) {
        if (!DrivePreferences.isAutoBackupEnabled(context)) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeRequest = OneTimeWorkRequestBuilder<AutoDriveBackupWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            AutoDriveBackupWorker.MANUAL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )
    }
}
