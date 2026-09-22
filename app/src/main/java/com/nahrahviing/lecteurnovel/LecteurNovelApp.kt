package com.nahrahviing.lecteurnovel

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nahrahviing.lecteurnovel.data.network.parsers.dynamic.DynamicParserManager
import com.nahrahviing.lecteurnovel.util.ChapterUpdateManager
import com.nahrahviing.lecteurnovel.util.NotificationHelper
import com.nahrahviing.lecteurnovel.util.UpdatePreferences
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LecteurNovelApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.initChannels(this)
        DynamicParserManager.init(this)
        if (UpdatePreferences.isAutoUpdateEnabled(this)) {
            ChapterUpdateManager.scheduleDailyUpdate(this)
        }
        if (com.nahrahviing.lecteurnovel.util.DrivePreferences.isAutoBackupEnabled(this)) {
            com.nahrahviing.lecteurnovel.util.AutoDriveBackupManager.schedulePeriodicBackup(this)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
