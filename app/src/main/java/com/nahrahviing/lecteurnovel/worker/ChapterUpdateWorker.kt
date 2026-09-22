package com.nahrahviing.lecteurnovel.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nahrahviing.lecteurnovel.data.local.ChapterEntity
import com.nahrahviing.lecteurnovel.data.network.parsers.ParserManager
import com.nahrahviing.lecteurnovel.data.repository.NovelRepository
import com.nahrahviing.lecteurnovel.util.NotificationHelper
import com.nahrahviing.lecteurnovel.util.UpdatePreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class ChapterUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: NovelRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        NotificationHelper.initChannels(context)

        try {
            val savedNovels = repository.getAllSavedNovelsSync()
            if (savedNovels.isEmpty()) {
                UpdatePreferences.setLastCheckInfo(context, System.currentTimeMillis(), 0)
                return@withContext Result.success(workDataOf(KEY_NEW_CHAPTERS_COUNT to 0))
            }

            var totalNewChaptersFound = 0

            for (savedNovel in savedNovels) {
                if (isStopped) break

                try {
                    val onlineNovel = ParserManager.extractNovel(savedNovel.originalUrl) ?: continue
                    val existingChapters = repository.getChaptersForNovel(savedNovel.originalUrl)
                    val existingUrls = existingChapters.map { it.url }.toSet()

                    val newChapters = onlineNovel.chapters.filter { it.url !in existingUrls }

                    if (newChapters.isNotEmpty()) {
                        val syncedCount = repository.syncNewChaptersForNovel(savedNovel, newChapters)
                        totalNewChaptersFound += syncedCount

                        val detailMsg = if (savedNovel.exportMode == "SINGLE_EPUB") {
                            "${savedNovel.title} : $syncedCount nouveau(x) chapitre(s) intégré(s) à votre EPUB !"
                        } else {
                            "${savedNovel.title} : $syncedCount nouveau(x) fichier(s) de chapitre ajouté(s) !"
                        }

                        NotificationHelper.showChapterUpdateNotification(
                            context = context,
                            novelTitle = savedNovel.title,
                            newChaptersCount = syncedCount,
                            notificationId = savedNovel.originalUrl.hashCode(),
                            detailText = detailMsg
                        )
                    }
                } catch (_: Exception) {
                }
            }

            UpdatePreferences.setLastCheckInfo(context, System.currentTimeMillis(), totalNewChaptersFound)

            if (totalNewChaptersFound > 0 && com.nahrahviing.lecteurnovel.util.DrivePreferences.isAutoBackupEnabled(context)) {
                com.nahrahviing.lecteurnovel.util.AutoDriveBackupManager.triggerImmediateBackup(context)
            }

            Result.success(workDataOf(KEY_NEW_CHAPTERS_COUNT to totalNewChaptersFound))
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "periodic_chapter_update_work"
        const val MANUAL_WORK_NAME = "manual_chapter_update_work"
        const val KEY_NEW_CHAPTERS_COUNT = "NEW_CHAPTERS_COUNT"
    }
}
