package com.nahrahviing.lecteurnovel.util

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import com.nahrahviing.lecteurnovel.worker.DownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class DownloadTask(
    val id: String,
    val novelUrl: String,
    val novelTitle: String,
    val coverUrl: String,
    val author: String,
    val format: String,             // EPUB, PDF, ODT, TXT
    val exportMode: String,         // SINGLE_EPUB, SEPARATE_CHAPTERS
    val startChap: Int,
    val endChap: Int,
    val totalChapters: Int,
    val currentChapter: Int = 0,
    val progress: Float = 0f,
    val statusMessage: String = "En attente...",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val outputPath: String? = null,
    val fileSizeBytes: Long = 0L,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null
)

/**
 * Gestionnaire de file d'attente de téléchargements asynchrones.
 * 
 * S'appuie sur Android WorkManager pour garantir l'exécution même lorsque l'application passe en arrière-plan
 * ou que l'écran est éteint. Fournit un flux d'état réactif (StateFlow) pour l'écran de téléchargement.
 */
object DownloadQueueManager {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    private fun updateActiveCount() {
        _activeCount.value = _tasks.value.count {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
        }
    }

    fun enqueueDownload(
        context: Context,
        novel: NovelInfo,
        format: String,
        exportMode: String = "SINGLE_EPUB",
        startChap: Int = 1,
        endChap: Int = -1
    ): String {
        val totalChaps = novel.chapters.size
        val validStart = maxOf(1, startChap)
        val validEnd = if (endChap == -1) totalChaps else minOf(totalChaps, endChap)
        val targetCount = maxOf(0, validEnd - validStart + 1)

        val data = workDataOf(
            DownloadWorker.KEY_NOVEL_URL to novel.originalUrl,
            DownloadWorker.KEY_NOVEL_TITLE to novel.title,
            DownloadWorker.KEY_FORMAT to format,
            DownloadWorker.KEY_EXPORT_MODE to exportMode,
            DownloadWorker.KEY_START_CHAP to validStart,
            DownloadWorker.KEY_END_CHAP to validEnd
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("novel_download")
            .build()

        val taskId = workRequest.id.toString()

        val newTask = DownloadTask(
            id = taskId,
            novelUrl = novel.originalUrl,
            novelTitle = novel.title.ifBlank { "Roman inconnu" },
            coverUrl = novel.coverUrl,
            author = novel.author,
            format = format,
            exportMode = exportMode,
            startChap = validStart,
            endChap = validEnd,
            totalChapters = targetCount,
            currentChapter = 0,
            progress = 0f,
            statusMessage = "En attente dans la file...",
            status = DownloadStatus.QUEUED
        )

        _tasks.value = listOf(newTask) + _tasks.value
        updateActiveCount()

        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(workRequest)

        // Observer le statut du worker pour mettre à jour la file
        scope.launch {
            workManager.getWorkInfoByIdFlow(workRequest.id).collectLatest { workInfo ->
                if (workInfo != null) {
                    handleWorkInfoUpdate(taskId, workInfo)
                }
            }
        }

        return taskId
    }

    private fun handleWorkInfoUpdate(taskId: String, workInfo: WorkInfo) {
        val currentTasks = _tasks.value.toMutableList()
        val index = currentTasks.indexOfFirst { it.id == taskId }
        if (index < 0) return

        val existing = currentTasks[index]
        when (workInfo.state) {
            WorkInfo.State.ENQUEUED -> {
                currentTasks[index] = existing.copy(
                    status = DownloadStatus.QUEUED,
                    statusMessage = "En attente dans la file..."
                )
            }
            WorkInfo.State.RUNNING -> {
                val progress = workInfo.progress.getFloat(DownloadWorker.KEY_PROGRESS, existing.progress)
                val statusMsg = workInfo.progress.getString(DownloadWorker.KEY_STATUS) ?: existing.statusMessage
                val currentChap = (progress * existing.totalChapters).toInt().coerceIn(0, existing.totalChapters)
                currentTasks[index] = existing.copy(
                    status = DownloadStatus.DOWNLOADING,
                    progress = progress,
                    currentChapter = currentChap,
                    statusMessage = statusMsg.ifBlank { "Téléchargement en cours..." }
                )
            }
            WorkInfo.State.SUCCEEDED -> {
                val outPath = workInfo.outputData.getString("OUTPUT_PATH") ?: existing.outputPath
                var fileSize = 0L
                if (outPath != null) {
                    val f = File(outPath)
                    if (f.exists()) {
                        fileSize = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
                    }
                }
                currentTasks[index] = existing.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 1f,
                    currentChapter = existing.totalChapters,
                    statusMessage = "✓ Téléchargement terminé",
                    outputPath = outPath,
                    fileSizeBytes = fileSize,
                    finishedAt = System.currentTimeMillis()
                )
            }
            WorkInfo.State.FAILED -> {
                val error = workInfo.outputData.getString(DownloadWorker.KEY_ERROR) ?: "Erreur de téléchargement"
                currentTasks[index] = existing.copy(
                    status = DownloadStatus.FAILED,
                    statusMessage = error,
                    errorMessage = error,
                    finishedAt = System.currentTimeMillis()
                )
            }
            WorkInfo.State.CANCELLED -> {
                currentTasks[index] = existing.copy(
                    status = DownloadStatus.CANCELLED,
                    statusMessage = "Téléchargement annulé",
                    finishedAt = System.currentTimeMillis()
                )
            }
            else -> {}
        }

        _tasks.value = currentTasks
        updateActiveCount()
    }

    fun cancelTask(context: Context, taskId: String) {
        try {
            WorkManager.getInstance(context).cancelWorkById(UUID.fromString(taskId))
            val currentTasks = _tasks.value.toMutableList()
            val index = currentTasks.indexOfFirst { it.id == taskId }
            if (index >= 0) {
                currentTasks[index] = currentTasks[index].copy(
                    status = DownloadStatus.CANCELLED,
                    statusMessage = "Téléchargement annulé",
                    finishedAt = System.currentTimeMillis()
                )
                _tasks.value = currentTasks
                updateActiveCount()
            }
        } catch (_: Exception) {
        }
    }

    fun cancelAll(context: Context) {
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag("novel_download")
            val currentTasks = _tasks.value.map { task ->
                if (task.status == DownloadStatus.QUEUED || task.status == DownloadStatus.DOWNLOADING) {
                    task.copy(
                        status = DownloadStatus.CANCELLED,
                        statusMessage = "Téléchargement annulé",
                        finishedAt = System.currentTimeMillis()
                    )
                } else {
                    task
                }
            }
            _tasks.value = currentTasks
            updateActiveCount()
        } catch (_: Exception) {
        }
    }

    fun deleteDownloadedFilesForTask(context: Context, taskId: String): Boolean {
        val task = _tasks.value.find { it.id == taskId } ?: return false
        var deleted = false
        task.outputPath?.let { path ->
            try {
                val f = File(path)
                if (f.exists()) {
                    deleted = if (f.isDirectory) f.deleteRecursively() else f.delete()
                }
            } catch (_: Exception) {
            }
        }

        // Retirer la tâche de la file
        removeFromQueue(taskId)
        return deleted
    }

    fun removeFromQueue(taskId: String) {
        _tasks.value = _tasks.value.filterNot { it.id == taskId }
        updateActiveCount()
    }

    fun relaunchTask(context: Context, taskId: String): String? {
        val task = _tasks.value.find { it.id == taskId } ?: return null

        // Si la tâche est en cours ou en attente, annuler l'ancienne
        if (task.status == DownloadStatus.QUEUED || task.status == DownloadStatus.DOWNLOADING) {
            cancelTask(context, taskId)
        }

        val data = workDataOf(
            DownloadWorker.KEY_NOVEL_URL to task.novelUrl,
            DownloadWorker.KEY_NOVEL_TITLE to task.novelTitle,
            DownloadWorker.KEY_FORMAT to task.format,
            DownloadWorker.KEY_EXPORT_MODE to task.exportMode,
            DownloadWorker.KEY_START_CHAP to task.startChap,
            DownloadWorker.KEY_END_CHAP to task.endChap
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("novel_download")
            .build()

        val newTaskId = workRequest.id.toString()

        val updatedTask = task.copy(
            id = newTaskId,
            currentChapter = 0,
            progress = 0f,
            statusMessage = "En attente dans la file...",
            status = DownloadStatus.QUEUED,
            errorMessage = null,
            finishedAt = null
        )

        // Remplacer l'ancienne tâche dans la liste
        _tasks.value = _tasks.value.map { if (it.id == taskId) updatedTask else it }
        updateActiveCount()

        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(workRequest)

        scope.launch {
            workManager.getWorkInfoByIdFlow(workRequest.id).collectLatest { workInfo ->
                if (workInfo != null) {
                    handleWorkInfoUpdate(newTaskId, workInfo)
                }
            }
        }

        return newTaskId
    }

    fun cancelExistingAndEnqueue(
        context: Context,
        novel: NovelInfo,
        format: String,
        exportMode: String = "SINGLE_EPUB",
        startChap: Int = 1,
        endChap: Int = -1
    ): String {
        val activeTasks = _tasks.value.filter {
            it.novelUrl == novel.originalUrl &&
            (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING)
        }
        for (t in activeTasks) {
            cancelTask(context, t.id)
        }
        return enqueueDownload(context, novel, format, exportMode, startChap, endChap)
    }

    fun getActiveTaskForNovel(novelUrl: String): DownloadTask? {
        return _tasks.value.find {
            it.novelUrl == novelUrl &&
            (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING)
        }
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filter {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
        }
        updateActiveCount()
    }
}
