package com.nahrahviing.lecteurnovel.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.nahrahviing.lecteurnovel.data.backup.BackupManager
import com.nahrahviing.lecteurnovel.data.repository.NovelRepository
import com.nahrahviing.lecteurnovel.data.sync.GoogleDriveSyncManager
import com.nahrahviing.lecteurnovel.util.DrivePreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class AutoDriveBackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupManager: BackupManager,
    private val novelRepository: NovelRepository,
    private val googleDriveSyncManager: GoogleDriveSyncManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "AutoDriveBackupPeriodicWork"
        const val MANUAL_WORK_NAME = "AutoDriveBackupManualWork"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!DrivePreferences.isAutoBackupEnabled(context)) {
            return@withContext Result.success()
        }

        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null) {
            DrivePreferences.setLastBackupInfo(context, System.currentTimeMillis(), "Échec : non connecté")
            return@withContext Result.success()
        }

        try {
            // 1. Sauvegarder la base de données de la bibliothèque (JSON)
            val backupFile = backupManager.exportBackupToCacheFile(context)
            val backupResult = googleDriveSyncManager.uploadBackupToDrive(context, account, backupFile)

            if (!backupResult.isSuccess) {
                val err = backupResult.exceptionOrNull()?.message ?: "Erreur inconnue"
                DrivePreferences.setLastBackupInfo(context, System.currentTimeMillis(), "Échec : $err")
                return@withContext Result.retry()
            }

            var epubsSynced = 0
            // 2. Synchroniser les fichiers EPUB si l'option est activée
            if (DrivePreferences.isAutoEpubSyncEnabled(context)) {
                val savedNovels = novelRepository.getAllSavedNovelsSync()
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

                // Vérifier également le dossier standard des epubs
                val epubsDir = File(context.filesDir, "epubs")
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

                if (epubsToSync.isNotEmpty()) {
                    val epubResult = googleDriveSyncManager.syncAllLocalEpubsToDrive(context, account, epubsToSync)
                    epubsSynced = epubResult.getOrDefault(0)
                }
            }

            val statusMsg = if (epubsSynced > 0) {
                "Réussie ($epubsSynced EPUB(s) synchronisé(s))"
            } else {
                "Réussie"
            }

            DrivePreferences.setLastBackupInfo(context, System.currentTimeMillis(), statusMsg)
            Result.success()
        } catch (e: Exception) {
            DrivePreferences.setLastBackupInfo(context, System.currentTimeMillis(), "Échec : ${e.message}")
            Result.retry()
        }
    }
}
