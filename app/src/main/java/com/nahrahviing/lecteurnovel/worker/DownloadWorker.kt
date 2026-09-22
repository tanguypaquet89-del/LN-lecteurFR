package com.nahrahviing.lecteurnovel.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import com.nahrahviing.lecteurnovel.data.network.parsers.ParserManager
import com.nahrahviing.lecteurnovel.data.repository.NovelRepository
import com.nahrahviing.lecteurnovel.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: NovelRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val KEY_NOVEL_URL = "NOVEL_URL"
        const val KEY_NOVEL_TITLE = "NOVEL_TITLE"
        const val KEY_FORMAT = "FORMAT"
        const val KEY_EXPORT_MODE = "EXPORT_MODE"
        const val KEY_START_CHAP = "START_CHAP"
        const val KEY_END_CHAP = "END_CHAP"
        const val KEY_PROGRESS = "PROGRESS"
        const val KEY_STATUS = "STATUS"
        const val KEY_ERROR = "ERROR"
    }

    private fun getNotificationId(): Int {
        return 1000 + (id.hashCode() and 0x7FFFFFFF) % 10000
    }

    private fun createForegroundInfo(
        title: String,
        current: Int,
        total: Int,
        status: String
    ): ForegroundInfo {
        val cancelPendingIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
        val notifId = getNotificationId()
        val notification = NotificationHelper.createDownloadNotification(
            context = context,
            novelTitle = title,
            progress = current,
            max = total,
            statusText = status,
            cancelPendingIntent = cancelPendingIntent
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notifId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notifId, notification)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val novelUrl = inputData.getString(KEY_NOVEL_URL) ?: return@withContext Result.failure()
        val format = inputData.getString(KEY_FORMAT) ?: "EPUB"
        val exportMode = inputData.getString(KEY_EXPORT_MODE) ?: "SINGLE_EPUB"
        val startChap = inputData.getInt(KEY_START_CHAP, 1)
        val endChap = inputData.getInt(KEY_END_CHAP, -1)
        var novelTitle = inputData.getString(KEY_NOVEL_TITLE) ?: "Roman"

        NotificationHelper.initChannels(context)

        try {
            // Affichage immédiat du service foreground avec la notification persistante
            setForeground(createForegroundInfo(novelTitle, 0, 100, "Analyse du roman..."))
            setProgress(workDataOf(KEY_PROGRESS to 0f, KEY_STATUS to "Analyse du roman..."))

            val novel = ParserManager.extractNovel(novelUrl) ?: return@withContext Result.failure()
            if (novel.title.isNotBlank()) {
                novelTitle = novel.title
            }

            if (isStopped) {
                return@withContext Result.failure()
            }

            val validStart = maxOf(1, startChap)
            val validEnd = if (endChap == -1) novel.chapters.size else minOf(novel.chapters.size, endChap)

            if (validStart > validEnd || novel.chapters.isEmpty()) {
                return@withContext Result.failure()
            }

            val subset = novel.chapters.subList(validStart - 1, validEnd)
            val modeLabel = when {
                exportMode == "SEPARATE_CHAPTERS" -> "fichiers séparés"
                else -> "$format unique"
            }

            val outPath = repository.downloadNovelWithConfig(
                novel = novel,
                format = format,
                exportMode = exportMode,
                chaptersSubset = subset
            ) { current, total ->
                if (isStopped) return@downloadNovelWithConfig
                val progressRatio = current.toFloat() / total.toFloat()
                val statusText = "Téléchargement ($format - $modeLabel) : $current / $total"

                setProgressAsync(workDataOf(
                    KEY_PROGRESS to progressRatio,
                    KEY_STATUS to statusText
                ))

                // Mise à jour de la notification du ForegroundService
                setForegroundAsync(createForegroundInfo(novelTitle, current, total, statusText))
            }

            if (isStopped) {
                return@withContext Result.failure()
            }

            // Notification finale de succès
            NotificationHelper.showDownloadCompleteNotification(context, novelTitle, getNotificationId() + 1)
            setProgress(workDataOf(
                KEY_PROGRESS to 1f,
                KEY_STATUS to "Terminé",
                "OUTPUT_PATH" to outPath
            ))

            // Sauvegarde automatique et téléversement EPUB vers Google Drive si activé
            if (com.nahrahviing.lecteurnovel.util.DrivePreferences.isAutoBackupEnabled(context)) {
                com.nahrahviing.lecteurnovel.util.AutoDriveBackupManager.triggerImmediateBackup(context)
            }

            Result.success(workDataOf("OUTPUT_PATH" to outPath))
        } catch (e: Exception) {
            NotificationHelper.cancelNotification(context, getNotificationId())
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to (e.localizedMessage ?: "Erreur de téléchargement")))
            }
        }
    }
}
