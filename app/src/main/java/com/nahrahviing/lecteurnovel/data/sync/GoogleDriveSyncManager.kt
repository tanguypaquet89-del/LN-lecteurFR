package com.nahrahviing.lecteurnovel.data.sync

import android.content.Context
import com.nahrahviing.lecteurnovel.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveSyncManager @Inject constructor() {

    private val BACKUP_FILE_NAME = "lecteur_novel_auto_backup.json"

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    suspend fun uploadBackupToDrive(context: Context, account: GoogleSignInAccount, backupFile: File): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_APPDATA)
            )
            if (account.account != null) {
                credential.selectedAccount = account.account
            } else if (!account.email.isNullOrEmpty()) {
                credential.selectedAccountName = account.email
            }

            val driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("LecteurNovel").build()

            // 1. Chercher si un fichier de sauvegarde existe déjà dans le dossier caché AppData
            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$BACKUP_FILE_NAME'")
                .setFields("files(id, name)")
                .execute()

            val existingFileId = fileList.files.firstOrNull()?.id

            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = BACKUP_FILE_NAME
                parents = Collections.singletonList("appDataFolder")
                mimeType = "application/json"
            }

            val mediaContent = FileContent("application/json", backupFile)

            if (existingFileId != null) {
                // Mettre à jour le fichier existant
                val updatedFileMetadata = com.google.api.services.drive.model.File()
                driveService.files().update(existingFileId, updatedFileMetadata, mediaContent)
                    .execute()
            } else {
                // Créer un nouveau fichier
                driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadBackupFromDrive(context: Context, account: GoogleSignInAccount, outputFile: File): Result<File?> = withContext(Dispatchers.IO) {
        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_APPDATA)
            )
            if (account.account != null) {
                credential.selectedAccount = account.account
            } else if (!account.email.isNullOrEmpty()) {
                credential.selectedAccountName = account.email
            }

            val driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("LecteurNovel").build()

            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$BACKUP_FILE_NAME'")
                .setFields("files(id, name)")
                .execute()

            val existingFileId = fileList.files.firstOrNull()?.id

            if (existingFileId != null) {
                val outputStream = FileOutputStream(outputFile)
                driveService.files().get(existingFileId).executeMediaAndDownloadTo(outputStream)
                outputStream.close()
                Result.success(outputFile)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getDriveService(context: Context, account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_APPDATA)
        )
        if (account.account != null) {
            credential.selectedAccount = account.account
        } else if (!account.email.isNullOrEmpty()) {
            credential.selectedAccountName = account.email
        }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("LecteurNovel").build()
    }

    suspend fun uploadEpubToDrive(
        context: Context,
        account: GoogleSignInAccount,
        epubFile: File,
        novelTitle: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!epubFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Le fichier EPUB n'existe pas localement."))
            }

            val driveService = getDriveService(context, account)
            val cleanTitle = novelTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
            val fileName = "$cleanTitle.epub"

            // Vérifier si un EPUB du même nom existe déjà dans appDataFolder
            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$fileName'")
                .setFields("files(id, name)")
                .execute()

            val existingFileId = fileList.files.firstOrNull()?.id
            val mediaContent = FileContent("application/epub+zip", epubFile)

            if (existingFileId != null) {
                val updateMeta = com.google.api.services.drive.model.File()
                driveService.files().update(existingFileId, updateMeta, mediaContent).execute()
            } else {
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = fileName
                    parents = Collections.singletonList("appDataFolder")
                    mimeType = "application/epub+zip"
                    description = "Roman EPUB : $novelTitle"
                }
                driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncAllLocalEpubsToDrive(
        context: Context,
        account: GoogleSignInAccount,
        epubs: List<Pair<String, File>> // (Title, File)
    ): Result<Int> = withContext(Dispatchers.IO) {
        var successCount = 0
        var lastException: Exception? = null

        for ((title, file) in epubs) {
            if (file.exists() && file.isFile) {
                val res = uploadEpubToDrive(context, account, file, title)
                if (res.isSuccess) {
                    successCount++
                } else {
                    lastException = res.exceptionOrNull() as? Exception
                }
            }
        }

        if (successCount > 0 || epubs.isEmpty()) {
            Result.success(successCount)
        } else {
            Result.failure(lastException ?: Exception("Aucun fichier EPUB n'a pu être téléversé."))
        }
    }

    suspend fun listEpubsOnDrive(
        context: Context,
        account: GoogleSignInAccount
    ): Result<List<DriveEpubInfo>> = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService(context, account)
            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("mimeType = 'application/epub+zip' or name contains '.epub'")
                .setFields("files(id, name, size)")
                .execute()

            val results = fileList.files.map { file ->
                DriveEpubInfo(
                    id = file.id,
                    name = file.name ?: "Sans nom.epub",
                    sizeBytes = file.getSize() ?: 0L
                )
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadEpubFromDrive(
        context: Context,
        account: GoogleSignInAccount,
        fileId: String,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService(context, account)
            val outputStream = FileOutputStream(outputFile)
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            outputStream.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class DriveEpubInfo(
    val id: String,
    val name: String,
    val sizeBytes: Long = 0L
)
