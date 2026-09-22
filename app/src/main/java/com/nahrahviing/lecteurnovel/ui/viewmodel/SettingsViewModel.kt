package com.nahrahviing.lecteurnovel.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.nahrahviing.lecteurnovel.data.backup.BackupManager
import com.nahrahviing.lecteurnovel.data.backup.BackupValidationResult
import com.nahrahviing.lecteurnovel.data.backup.LibraryBackup
import com.nahrahviing.lecteurnovel.data.backup.RestoreResult
import com.nahrahviing.lecteurnovel.data.network.parsers.dynamic.DynamicParserConfig
import com.nahrahviing.lecteurnovel.data.network.parsers.dynamic.DynamicParserManager
import com.nahrahviing.lecteurnovel.data.repository.NovelRepository
import com.nahrahviing.lecteurnovel.util.AppPreferences
import com.nahrahviing.lecteurnovel.util.ChapterUpdateManager
import com.nahrahviing.lecteurnovel.util.StorageCacheManager
import com.nahrahviing.lecteurnovel.util.StorageSummary
import com.nahrahviing.lecteurnovel.util.UpdatePreferences
import com.nahrahviing.lecteurnovel.worker.ChapterUpdateWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.nahrahviing.lecteurnovel.util.DownloadQueueManager
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import com.nahrahviing.lecteurnovel.data.model.Chapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.nahrahviing.lecteurnovel.util.DrivePreferences
import com.nahrahviing.lecteurnovel.util.AutoDriveBackupManager
import java.io.File

import com.nahrahviing.lecteurnovel.data.sync.P2PSyncManager
import com.nahrahviing.lecteurnovel.data.sync.P2PState
import com.nahrahviing.lecteurnovel.data.sync.GoogleDriveSyncManager
import android.widget.Toast
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: NovelRepository,
    private val backupManager: BackupManager,
    val p2pSyncManager: P2PSyncManager,
    val googleDriveSyncManager: GoogleDriveSyncManager,
    private val application: Application
) : ViewModel() {

    val dynamicParsers: StateFlow<List<DynamicParserConfig>> = DynamicParserManager.parsersConfig

    private val _storageSummary = MutableStateFlow<StorageSummary?>(null)
    val storageSummary: StateFlow<StorageSummary?> = _storageSummary.asStateFlow()

    private val _isAutoUpdateEnabled = MutableStateFlow(true)
    val isAutoUpdateEnabled: StateFlow<Boolean> = _isAutoUpdateEnabled.asStateFlow()

    private val _lastCheckTimestamp = MutableStateFlow(0L)
    val lastCheckTimestamp: StateFlow<Long> = _lastCheckTimestamp.asStateFlow()

    private val _lastNewChaptersCount = MutableStateFlow(0)
    val lastNewChaptersCount: StateFlow<Int> = _lastNewChaptersCount.asStateFlow()

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()

    private val _updateCheckResult = MutableStateFlow<String?>(null)
    val updateCheckResult: StateFlow<String?> = _updateCheckResult.asStateFlow()

    private val _isProcessingBackup = MutableStateFlow(false)
    val isProcessingBackup: StateFlow<Boolean> = _isProcessingBackup.asStateFlow()

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage.asStateFlow()

    private val _recoverableAuthIntent = MutableStateFlow<Intent?>(null)
    val recoverableAuthIntent: StateFlow<Intent?> = _recoverableAuthIntent.asStateFlow()

    private val _isAutoDriveBackupEnabled = MutableStateFlow(false)
    val isAutoDriveBackupEnabled: StateFlow<Boolean> = _isAutoDriveBackupEnabled.asStateFlow()

    private val _isAutoEpubSyncEnabled = MutableStateFlow(true)
    val isAutoEpubSyncEnabled: StateFlow<Boolean> = _isAutoEpubSyncEnabled.asStateFlow()

    private val _lastDriveBackupTimestamp = MutableStateFlow(0L)
    val lastDriveBackupTimestamp: StateFlow<Long> = _lastDriveBackupTimestamp.asStateFlow()

    private val _lastDriveBackupStatus = MutableStateFlow<String?>(null)
    val lastDriveBackupStatus: StateFlow<String?> = _lastDriveBackupStatus.asStateFlow()

    private val _driveConnectedEmail = MutableStateFlow<String?>(null)
    val driveConnectedEmail: StateFlow<String?> = _driveConnectedEmail.asStateFlow()

    private val appPreferences by lazy { AppPreferences(application) }

    private val _downloadChapterDelayMs = MutableStateFlow(400L)
    val downloadChapterDelayMs: StateFlow<Long> = _downloadChapterDelayMs.asStateFlow()

    fun setDownloadChapterDelayMs(delayMs: Long) {
        _downloadChapterDelayMs.value = delayMs
        appPreferences.downloadChapterDelayMs = delayMs
    }

    private val _autoDownloadOnRestore = MutableStateFlow(appPreferences.autoDownloadOnRestore)
    val autoDownloadOnRestore: StateFlow<Boolean> = _autoDownloadOnRestore.asStateFlow()

    fun setAutoDownloadOnRestore(enabled: Boolean) {
        _autoDownloadOnRestore.value = enabled
        appPreferences.autoDownloadOnRestore = enabled
    }

    private var lastAccount: GoogleSignInAccount? = null
    private var lastAction: String? = null

    fun clearRecoverableAuthIntent() {
        _recoverableAuthIntent.value = null
    }

    fun retryPendingDriveAction() {
        val account = lastAccount ?: return
        when (lastAction) {
            "backup" -> startDriveBackup(account)
            "restore" -> startDriveRestore(account)
            "sync_epubs" -> syncAllEpubs(account)
        }
    }

    init {
        _downloadChapterDelayMs.value = appPreferences.downloadChapterDelayMs
        refreshStorageSummary()
        loadUpdateSettings()
        loadDriveSettings()
    }

    private fun loadDriveSettings() {
        _isAutoDriveBackupEnabled.value = DrivePreferences.isAutoBackupEnabled(application)
        _isAutoEpubSyncEnabled.value = DrivePreferences.isAutoEpubSyncEnabled(application)
        _lastDriveBackupTimestamp.value = DrivePreferences.getLastBackupTimestamp(application)
        _lastDriveBackupStatus.value = DrivePreferences.getLastBackupStatus(application)
        
        val lastAcc = GoogleSignIn.getLastSignedInAccount(application)
        _driveConnectedEmail.value = lastAcc?.email ?: DrivePreferences.getConnectedAccountEmail(application)
    }

    fun setAutoDriveBackupEnabled(enabled: Boolean, account: GoogleSignInAccount? = null) {
        _isAutoDriveBackupEnabled.value = enabled
        DrivePreferences.setAutoBackupEnabled(application, enabled)
        if (account != null) {
            DrivePreferences.setConnectedAccountEmail(application, account.email)
            _driveConnectedEmail.value = account.email
        }
        if (enabled) {
            AutoDriveBackupManager.schedulePeriodicBackup(application)
            AutoDriveBackupManager.triggerImmediateBackup(application)
            _backupStatusMessage.value = "Sauvegarde automatique sur Google Drive activée"
        } else {
            AutoDriveBackupManager.cancelPeriodicBackup(application)
            _backupStatusMessage.value = "Sauvegarde automatique désactivée"
        }
        loadDriveSettings()
    }

    fun setAutoEpubSyncEnabled(enabled: Boolean) {
        _isAutoEpubSyncEnabled.value = enabled
        DrivePreferences.setAutoEpubSyncEnabled(application, enabled)
        _backupStatusMessage.value = if (enabled) {
            "Enregistrement automatique des EPUB sur Drive activé"
        } else {
            "Enregistrement des EPUB sur Drive désactivé"
        }
    }

    fun syncAllEpubs(account: GoogleSignInAccount) {
        lastAccount = account
        lastAction = "sync_epubs"
        _driveConnectedEmail.value = account.email
        DrivePreferences.setConnectedAccountEmail(application, account.email)

        viewModelScope.launch {
            _isProcessingBackup.value = true
            _backupStatusMessage.value = "Synchronisation des fichiers EPUB sur Drive..."
            try {
                val savedNovels = repository.getAllSavedNovelsSync()
                val epubsToSync = mutableListOf<Pair<String, File>>()
                for (novel in savedNovels) {
                    val path = novel.epubFilePath
                    if (!path.isNullOrBlank()) {
                        val file = File(path)
                        if (file.exists() && file.isFile) {
                            epubsToSync.add(novel.title to file)
                        }
                    }
                }
                val epubsDir = File(application.filesDir, "epubs")
                if (epubsDir.exists() && epubsDir.isDirectory) {
                    epubsDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.name.endsWith(".epub", ignoreCase = true)) {
                            val title = file.nameWithoutExtension
                            if (epubsToSync.none { it.second.absolutePath == file.absolutePath }) {
                                epubsToSync.add(title to file)
                            }
                        }
                    }
                }

                if (epubsToSync.isEmpty()) {
                    _backupStatusMessage.value = "Aucun fichier EPUB local trouvé à synchroniser."
                } else {
                    val result = googleDriveSyncManager.syncAllLocalEpubsToDrive(application, account, epubsToSync)
                    if (result.isSuccess) {
                        val count = result.getOrDefault(0)
                        _backupStatusMessage.value = "✓ $count fichier(s) EPUB synchronisé(s) sur Google Drive !"
                        DrivePreferences.setLastBackupInfo(application, System.currentTimeMillis(), "EPUBs synchronisés ($count)")
                        loadDriveSettings()
                    } else {
                        val ex = result.exceptionOrNull()
                        if (ex is com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                            _recoverableAuthIntent.value = ex.intent
                            _backupStatusMessage.value = "Autorisation Google Drive requise..."
                        } else {
                            _backupStatusMessage.value = "✗ Erreur: ${ex?.message ?: "Échec de synchronisation"}"
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                    _recoverableAuthIntent.value = e.intent
                    _backupStatusMessage.value = "Autorisation Google Drive requise..."
                } else {
                    _backupStatusMessage.value = "✗ Erreur: ${e.message}"
                }
            } finally {
                _isProcessingBackup.value = false
            }
        }
    }

    private fun loadUpdateSettings() {
        _isAutoUpdateEnabled.value = UpdatePreferences.isAutoUpdateEnabled(application)
        _lastCheckTimestamp.value = UpdatePreferences.getLastCheckTimestamp(application)
        _lastNewChaptersCount.value = UpdatePreferences.getLastNewChaptersCount(application)
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        _isAutoUpdateEnabled.value = enabled
        UpdatePreferences.setAutoUpdateEnabled(application, enabled)
        if (enabled) {
            ChapterUpdateManager.scheduleDailyUpdate(application)
        } else {
            ChapterUpdateManager.cancelDailyUpdate(application)
        }
    }

    fun checkForUpdatesNow() {
        if (_isCheckingUpdates.value) return
        _isCheckingUpdates.value = true
        _updateCheckResult.value = "Vérification en cours..."

        val workId = ChapterUpdateManager.checkNow(application)
        val workManager = WorkManager.getInstance(application)

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collectLatest { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            _isCheckingUpdates.value = false
                            val newCount = workInfo.outputData.getInt(ChapterUpdateWorker.KEY_NEW_CHAPTERS_COUNT, 0)
                            loadUpdateSettings()
                            _updateCheckResult.value = if (newCount > 0) {
                                "✓ $newCount nouveau(x) chapitre(s) détecté(s) !"
                            } else {
                                "✓ Tous vos romans sont à jour."
                            }
                        }
                        WorkInfo.State.FAILED -> {
                            _isCheckingUpdates.value = false
                            _updateCheckResult.value = "Erreur lors de la vérification (connexion internet requise)."
                        }
                        WorkInfo.State.CANCELLED -> {
                            _isCheckingUpdates.value = false
                            _updateCheckResult.value = "Vérification annulée."
                        }
                        else -> {
                            // En cours...
                        }
                    }
                }
            }
        }
    }

    fun refreshStorageSummary() {
        viewModelScope.launch {
            val count = repository.getAllSavedNovels().first().size
            _storageSummary.value = StorageCacheManager.getStorageSummary(application, count)
        }
    }

    fun clearCache() {
        StorageCacheManager.clearCache(application)
        refreshStorageSummary()
    }

    fun importParserJson(jsonStr: String): Pair<Boolean, String> {
        val result = DynamicParserManager.importParser(application, jsonStr)
        return if (result.isSuccess) {
            val cfg = result.getOrNull()
            true to "Parseur '${cfg?.name}' importé avec succès !"
        } else {
            false to (result.exceptionOrNull()?.localizedMessage ?: "Erreur lors de l'import du JSON.")
        }
    }

    fun deleteParser(id: String) {
        DynamicParserManager.deleteCustomParser(application, id)
    }

    fun resetParsersToDefault() {
        DynamicParserManager.resetToDefaults(application)
    }

    // ==========================================
    // Sauvegarde & Restauration de la Bibliothèque
    // ==========================================

    

    

    

    fun startDriveBackup(account: GoogleSignInAccount) {
        lastAccount = account
        lastAction = "backup"
        viewModelScope.launch {
            _isProcessingBackup.value = true
            _backupStatusMessage.value = "Sauvegarde sur Google Drive en cours..."
            try {
                val file = backupManager.exportBackupToCacheFile(application)
                val result = googleDriveSyncManager.uploadBackupToDrive(application, account, file)
                if (result.isSuccess && result.getOrDefault(false)) {
                    _backupStatusMessage.value = "✓ Sauvegarde Google Drive réussie"
                    DrivePreferences.setLastBackupInfo(application, System.currentTimeMillis(), "Réussie")
                    DrivePreferences.setConnectedAccountEmail(application, account.email)
                    loadDriveSettings()
                } else {
                    val ex = result.exceptionOrNull()
                    if (ex is com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                        _recoverableAuthIntent.value = ex.intent
                        _backupStatusMessage.value = "Autorisation Google Drive requise..."
                    } else {
                        _backupStatusMessage.value = "✗ Erreur: ${ex?.message ?: "Échec de sauvegarde"}"
                    }
                }
            } catch (e: Exception) {
                if (e is com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                    _recoverableAuthIntent.value = e.intent
                    _backupStatusMessage.value = "Autorisation Google Drive requise..."
                } else {
                    _backupStatusMessage.value = "✗ Erreur: ${e.message}"
                }
            } finally {
                _isProcessingBackup.value = false
            }
        }
    }

    fun startDriveRestore(account: GoogleSignInAccount) {
        lastAccount = account
        lastAction = "restore"
        viewModelScope.launch {
            _isProcessingBackup.value = true
            _backupStatusMessage.value = "Téléchargement depuis Google Drive..."
            try {
                val cacheFile = File(application.cacheDir, "drive_restore.json")
                val result = googleDriveSyncManager.downloadBackupFromDrive(application, account, cacheFile)
                val downloadedFile = result.getOrNull()
                if (result.isSuccess && downloadedFile != null) {
                    _backupStatusMessage.value = "Restauration en cours..."
                    val jsonString = downloadedFile.readText(Charsets.UTF_8)
                    val validation = backupManager.validateBackupJson(jsonString)
                    if (!validation.isValid || validation.backup == null) {
                        _backupStatusMessage.value = "✗ Fichier de sauvegarde invalide: ${validation.errorMessage}"
                    } else {
                        val restoreResult = backupManager.restoreLibrary(application, validation.backup)
                        if (restoreResult.success) {
                            if (restoreResult.restoredNovelUrls.isNotEmpty()) {
                                autoRedownloadRestoredNovels(restoreResult.restoredNovelUrls)
                            }
                            _backupStatusMessage.value = "✓ Restauration réussie (${restoreResult.restoredNovelsCount} roman(s), ${restoreResult.restoredStatsCount} stat(s))"
                            refreshStorageSummary()
                        } else {
                            _backupStatusMessage.value = "✗ Erreur restauration: ${restoreResult.message}"
                        }
                    }
                } else {
                    val ex = result.exceptionOrNull()
                    if (ex is com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                        _recoverableAuthIntent.value = ex.intent
                        _backupStatusMessage.value = "Autorisation Google Drive requise..."
                    } else {
                        _backupStatusMessage.value = "✗ Erreur: ${ex?.message ?: "Aucune sauvegarde trouvée sur Drive"}"
                    }
                }
            } catch (e: Exception) {
                if (e is com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                    _recoverableAuthIntent.value = e.intent
                    _backupStatusMessage.value = "Autorisation Google Drive requise..."
                } else {
                    _backupStatusMessage.value = "✗ Erreur: ${e.message}"
                }
            } finally {
                _isProcessingBackup.value = false
            }
        }
    }

    fun startP2PReceive() {
        p2pSyncManager.startDiscovery(android.os.Build.MODEL)
        Toast.makeText(application, "En attente de réception...", Toast.LENGTH_SHORT).show()
    }

    fun startP2PSend() {
        viewModelScope.launch {
            try {
                val file = backupManager.exportBackupToCacheFile(application)
                p2pSyncManager.startAdvertising(android.os.Build.MODEL, file)
                Toast.makeText(application, "Prêt à envoyer... (En attente de connexion)", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
            }
        }
    }
    
    
    private fun autoRedownloadRestoredNovels(urls: List<String>) {
        if (!_autoDownloadOnRestore.value) {
            android.widget.Toast.makeText(application, "✓ Restauration terminée (${urls.size} roman(s)). Téléchargement auto désactivé.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        android.widget.Toast.makeText(application, "Téléchargement automatique des chapitres manquants...", android.widget.Toast.LENGTH_LONG).show()
        viewModelScope.launch {
            for (url in urls) {
                val novel = repository.getSavedNovelByUrl(url) ?: continue
                var chapters = repository.getChaptersForNovel(novel.originalUrl)
                if (chapters.isEmpty()) {
                    try {
                        val extracted = com.nahrahviing.lecteurnovel.data.network.parsers.ParserManager.extractNovel(novel.originalUrl)
                        if (extracted != null && extracted.chapters.isNotEmpty()) {
                            repository.addNovelToLibraryWithoutDownload(extracted)
                            chapters = repository.getChaptersForNovel(novel.originalUrl)
                        }
                    } catch (_: Exception) {}
                }
                val isMissingFiles = chapters.any { !it.isDownloaded } || chapters.isEmpty()
                if (isMissingFiles) {
                    val novelInfo = NovelInfo(
                        title = novel.title,
                        author = novel.author,
                        coverUrl = novel.coverUrl,
                        synopsis = novel.synopsis,
                        genres = novel.genres.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        chapters = chapters.map { ch -> 
                            Chapter(
                                index = ch.chapterIndex,
                                title = ch.title,
                                url = ch.url,
                                content = ch.content
                            ) 
                        },
                        originalUrl = novel.originalUrl
                    )
                    DownloadQueueManager.enqueueDownload(
                        context = application,
                        novel = novelInfo,
                        format = "EPUB",
                        exportMode = if (novel.exportMode.isNotBlank()) novel.exportMode else "SINGLE_EPUB",
                        startChap = 1,
                        endChap = novelInfo.chapters.size
                    )
                }
            }
        }
    }

    fun simulateDriveConnection() {
        Toast.makeText(application, "Connexion à Google Drive (Simulation)...", Toast.LENGTH_SHORT).show()
    }


    fun generateDefaultBackupFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
        return "ln_lecteurfr_backup_${sdf.format(Date())}.json"
    }

    fun saveBackupToUri(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isProcessingBackup.value = true
            _backupStatusMessage.value = "Exportation en cours..."
            try {
                val jsonString = backupManager.exportBackupToJsonString(application)
                application.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                _backupStatusMessage.value = "✓ Sauvegarde exportée avec succès !"
                onResult(true, "Sauvegarde enregistrée avec succès !")
            } catch (e: Exception) {
                val err = "Erreur lors de l'enregistrement : ${e.localizedMessage ?: e.message}"
                _backupStatusMessage.value = err
                onResult(false, err)
            } finally {
                _isProcessingBackup.value = false
            }
        }
    }

    fun createBackupShareIntent(onReady: (Intent) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isProcessingBackup.value = true
            _backupStatusMessage.value = "Préparation du fichier de sauvegarde..."
            try {
                val file = backupManager.exportBackupToCacheFile(application)
                val uri = FileProvider.getUriForFile(
                    application,
                    "${application.packageName}.fileprovider",
                    file
                )
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Sauvegarde LN lecteurFR")
                    putExtra(Intent.EXTRA_TEXT, "Fichier de sauvegarde de bibliothèque et lectures LN lecteurFR.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(sendIntent, "Partager la sauvegarde JSON")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                _backupStatusMessage.value = null
                onReady(chooser)
            } catch (e: Exception) {
                val err = "Erreur de partage : ${e.localizedMessage ?: e.message}"
                _backupStatusMessage.value = err
                onError(err)
            } finally {
                _isProcessingBackup.value = false
            }
        }
    }

    fun getBackupJsonForClipboard(onReady: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isProcessingBackup.value = true
            try {
                val jsonStr = backupManager.exportBackupToJsonString(application)
                onReady(jsonStr)
            } catch (e: Exception) {
                onError("Erreur : ${e.localizedMessage ?: e.message}")
            } finally {
                _isProcessingBackup.value = false
            }
        }
    }

    fun validateBackupJson(jsonString: String): BackupValidationResult {
        return backupManager.validateBackupJson(jsonString)
    }

    fun restoreFromUri(uri: Uri, onResult: (RestoreResult) -> Unit) {
        viewModelScope.launch {
            _isProcessingBackup.value = true
            _backupStatusMessage.value = "Lecture du fichier..."
            try {
                val jsonString = application.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalArgumentException("Impossible de lire le fichier sélectionné.")

                val validation = backupManager.validateBackupJson(jsonString)
                if (!validation.isValid || validation.backup == null) {
                    val res = RestoreResult(false, 0, 0, 0, emptyList(), validation.errorMessage ?: "Fichier JSON invalide")
                    _backupStatusMessage.value = res.message
                    onResult(res)
                    return@launch
                }

                _backupStatusMessage.value = "Restauration des données..."
                val result = backupManager.restoreLibrary(application, validation.backup)
                if (result.success && result.restoredNovelUrls.isNotEmpty()) {
                    autoRedownloadRestoredNovels(result.restoredNovelUrls)
                }
                refreshStorageSummary()
                loadUpdateSettings()
                _backupStatusMessage.value = if (result.success) {
                    "✓ ${result.restoredNovelsCount} roman(s), ${result.restoredStatsCount} stat(s) restauré(s) !"
                } else {
                    result.message
                }
                onResult(result)
            } catch (e: Exception) {
                val res = RestoreResult(false, 0, 0, 0, emptyList(), "Erreur lors de la lecture : ${e.localizedMessage ?: e.message}")
                _backupStatusMessage.value = res.message
                onResult(res)
            } finally {
                _isProcessingBackup.value = false
            }
        }
    }

    fun restoreFromJsonText(jsonString: String, onResult: (RestoreResult) -> Unit) {
        viewModelScope.launch {
            _isProcessingBackup.value = true
            _backupStatusMessage.value = "Vérification du JSON..."
            try {
                val validation = backupManager.validateBackupJson(jsonString)
                if (!validation.isValid || validation.backup == null) {
                    val res = RestoreResult(false, 0, 0, 0, emptyList(), validation.errorMessage ?: "JSON invalide.")
                    _backupStatusMessage.value = res.message
                    onResult(res)
                    return@launch
                }

                _backupStatusMessage.value = "Restauration en cours..."
                val result = backupManager.restoreLibrary(application, validation.backup)
                if (result.success && result.restoredNovelUrls.isNotEmpty()) {
                    autoRedownloadRestoredNovels(result.restoredNovelUrls)
                }
                refreshStorageSummary()
                loadUpdateSettings()
                _backupStatusMessage.value = if (result.success) {
                    "✓ ${result.restoredNovelsCount} roman(s), ${result.restoredStatsCount} stat(s) restauré(s) !"
                } else {
                    result.message
                }
                onResult(result)
            } catch (e: Exception) {
                val res = RestoreResult(false, 0, 0, 0, emptyList(), "Erreur : ${e.localizedMessage ?: e.message}")
                _backupStatusMessage.value = res.message
                onResult(res)
            } finally {
                _isProcessingBackup.value = false
            }
        }
    }
}
