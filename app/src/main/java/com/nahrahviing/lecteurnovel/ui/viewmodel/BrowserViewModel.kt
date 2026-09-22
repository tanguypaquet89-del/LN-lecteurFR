package com.nahrahviing.lecteurnovel.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import com.nahrahviing.lecteurnovel.data.network.parsers.ParserManager
import com.nahrahviing.lecteurnovel.data.repository.NovelRepository
import com.nahrahviing.lecteurnovel.data.search.SupportedPlatforms
import com.nahrahviing.lecteurnovel.util.DownloadQueueManager
import com.nahrahviing.lecteurnovel.util.DownloadStatus
import com.nahrahviing.lecteurnovel.util.DownloadTask
import com.nahrahviing.lecteurnovel.worker.DownloadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repository: NovelRepository,
    private val application: Application
) : ViewModel() {

    val queueTasks: StateFlow<List<DownloadTask>> = DownloadQueueManager.tasks
    val activeDownloadsCount: StateFlow<Int> = DownloadQueueManager.activeCount
    private var currentTaskId: String? = null

    private val _browserUrl = MutableStateFlow(SupportedPlatforms.list.first().homeUrl)
    val browserUrl: StateFlow<String> = _browserUrl.asStateFlow()

    private val _detectedNovel = MutableStateFlow<NovelInfo?>(null)
    val detectedNovel: StateFlow<NovelInfo?> = _detectedNovel.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _downloadStatus = MutableStateFlow("")
    val downloadStatus: StateFlow<String> = _downloadStatus.asStateFlow()

    private val _debugMessage = MutableStateFlow<String>("")
    val debugMessage: StateFlow<String> = _debugMessage

    init {
        viewModelScope.launch {
            DownloadQueueManager.tasks.collectLatest { tasks ->
                val activeTasks = tasks.filter {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
                }
                _isDownloading.value = activeTasks.isNotEmpty()
                if (activeTasks.isNotEmpty()) {
                    val primaryTask = activeTasks.find { it.id == currentTaskId } ?: activeTasks.first()
                    _downloadProgress.value = primaryTask.progress
                    val extraInfo = if (activeTasks.size > 1) " (+${activeTasks.size - 1} autre(s))" else ""
                    _downloadStatus.value = "${primaryTask.novelTitle} : ${primaryTask.statusMessage}$extraInfo"
                } else {
                    val lastTask = tasks.firstOrNull()
                    if (lastTask != null && lastTask.status == DownloadStatus.COMPLETED) {
                        _downloadProgress.value = 1f
                        _downloadStatus.value = "✓ ${lastTask.novelTitle} : terminé"
                    }
                }
            }
        }
    }

    private var checkJob: kotlinx.coroutines.Job? = null
    private var currentlyCheckedUrl: String? = null

    fun setBrowserUrl(url: String) {
        val oldUrl = _browserUrl.value
        _browserUrl.value = url
        if (oldUrl != url && _detectedNovel.value?.originalUrl != url) {
            _detectedNovel.value = null
            _debugMessage.value = ""
        }
        if (currentlyCheckedUrl == url && (_detectedNovel.value != null || checkJob?.isActive == true)) {
            return
        }
        checkIfNovelPage(url)
    }

    private fun checkIfNovelPage(url: String) {
        if (!ParserManager.isNovelPage(url)) {
            checkJob?.cancel()
            currentlyCheckedUrl = null
            if (_detectedNovel.value?.originalUrl != url) {
                _detectedNovel.value = null
                _debugMessage.value = ""
            }
            return
        }
        if (currentlyCheckedUrl == url && _detectedNovel.value != null) {
            return
        }
        checkJob?.cancel()
        currentlyCheckedUrl = url
        checkJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            _debugMessage.value = "Détection du roman..."
            val novel = ParserManager.extractNovel(url)
            if (novel != null && novel.chapters.isNotEmpty()) {
                _debugMessage.value = "✓ ${novel.chapters.size} chapitres détectés !"
                _detectedNovel.value = novel
            } else if (_detectedNovel.value?.originalUrl != url) {
                _detectedNovel.value = null
            }
        }
    }

    fun onHtmlLoaded(url: String, html: String) {
        // Optionnel si besoin de fallback HTML, mais n'interfère pas si déjà extrait
        if (_detectedNovel.value != null && _detectedNovel.value?.originalUrl == url && (_detectedNovel.value?.chapters?.size ?: 0) > 0) {
            return
        }
        if (currentlyCheckedUrl == url && checkJob?.isActive == true) {
            return
        }
        if (ParserManager.isNovelPage(url)) {
            checkJob?.cancel()
            currentlyCheckedUrl = url
            checkJob = viewModelScope.launch {
                _debugMessage.value = "Extraction des chapitres..."
                val novel = ParserManager.extractNovel(url, html)
                if (novel != null && novel.chapters.isNotEmpty()) {
                    _debugMessage.value = "✓ ${novel.chapters.size} chapitres détectés !"
                    _detectedNovel.value = novel
                } else {
                    _debugMessage.value = "Aucun chapitre détecté sur cette page."
                    if (_detectedNovel.value?.originalUrl != url) {
                        _detectedNovel.value = null
                    }
                }
            }
        }
    }

    fun addNovelToLibrary(novel: NovelInfo, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addNovelToLibraryWithoutDownload(novel)
            _debugMessage.value = "✓ Roman ajouté à la bibliothèque !"
            onComplete()
        }
    }

        fun downloadNovelAdvanced(
        novel: NovelInfo,
        format: String,
        exportMode: String = "SINGLE_EPUB",
        startChap: Int,
        endChap: Int,
        onComplete: () -> Unit = {}
    ) {
        val taskId = DownloadQueueManager.enqueueDownload(
            context = application,
            novel = novel,
            format = format,
            exportMode = exportMode,
            startChap = startChap,
            endChap = endChap
        )
        currentTaskId = taskId
    }

    fun relaunchNovelDownload(
        novel: NovelInfo,
        format: String,
        exportMode: String = "SINGLE_EPUB",
        startChap: Int,
        endChap: Int
    ) {
        val taskId = DownloadQueueManager.cancelExistingAndEnqueue(
            context = application,
            novel = novel,
            format = format,
            exportMode = exportMode,
            startChap = startChap,
            endChap = endChap
        )
        currentTaskId = taskId
    }

    fun relaunchTask(taskId: String) {
        val newId = DownloadQueueManager.relaunchTask(application, taskId)
        if (newId != null) {
            currentTaskId = newId
        }
    }

    fun cancelCurrentDownload() {
        currentTaskId?.let { taskId ->
            DownloadQueueManager.cancelTask(application, taskId)
        } ?: run {
            val active = DownloadQueueManager.tasks.value.firstOrNull {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
            }
            if (active != null) {
                DownloadQueueManager.cancelTask(application, active.id)
            }
        }
    }

    fun cancelTask(taskId: String) {
        DownloadQueueManager.cancelTask(application, taskId)
    }

    fun cancelAllDownloads() {
        DownloadQueueManager.cancelAll(application)
        _isDownloading.value = false
    }

    fun deleteTaskFiles(taskId: String): Boolean {
        return DownloadQueueManager.deleteDownloadedFilesForTask(application, taskId)
    }

    fun removeFromQueue(taskId: String) {
        DownloadQueueManager.removeFromQueue(taskId)
    }

    fun clearFinishedQueue() {
        DownloadQueueManager.clearFinished()
    }

    fun downloadNovel(novel: NovelInfo, onComplete: () -> Unit = {}) {
        downloadNovelAdvanced(novel, "EPUB", "SINGLE_EPUB", 1, -1, onComplete)
    }
}
