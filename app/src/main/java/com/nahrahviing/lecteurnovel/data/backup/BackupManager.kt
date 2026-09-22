package com.nahrahviing.lecteurnovel.data.backup

import android.content.Context
import com.nahrahviing.lecteurnovel.data.local.BookmarkEntity
import com.nahrahviing.lecteurnovel.data.local.ChapterEntity
import com.nahrahviing.lecteurnovel.data.local.NovelDao
import com.nahrahviing.lecteurnovel.data.local.ReadingStatEntity
import com.nahrahviing.lecteurnovel.data.local.SavedNovelEntity
import com.nahrahviing.lecteurnovel.data.network.parsers.dynamic.DynamicParserManager
import com.nahrahviing.lecteurnovel.util.AppPreferences
import com.nahrahviing.lecteurnovel.util.UpdatePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val novelDao: NovelDao
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Exporte l'ensemble de la bibliothèque, de la progression et de la configuration au format JSON.
     */
    suspend fun createLibraryBackup(context: Context): LibraryBackup = withContext(Dispatchers.IO) {
        val savedNovels = novelDao.getAllSavedNovelsList()
        val backupNovels = savedNovels.map { novel ->
            val chapters = novelDao.getChaptersForNovel(novel.originalUrl)
            BackupNovel(
                originalUrl = novel.originalUrl,
                title = novel.title,
                author = novel.author,
                coverUrl = novel.coverUrl,
                synopsis = novel.synopsis,
                genres = novel.genres,
                totalChapters = novel.totalChapters,
                lastReadChapterIndex = novel.lastReadChapterIndex,
                lastReadPosition = novel.lastReadPosition,
                category = novel.category,
                isFavorite = novel.isFavorite,
                exportMode = novel.exportMode,
                epubFilePath = novel.epubFilePath,
                addedAt = novel.addedAt,
                lastReadAt = novel.lastReadAt,
                chapters = chapters.map { ch ->
                    BackupChapter(
                        chapterIndex = ch.chapterIndex,
                        title = ch.title,
                        url = ch.url,
                        isDownloaded = ch.isDownloaded,
                        filePath = ch.filePath
                    )
                }
            )
        }

        val allBookmarks = novelDao.getAllBookmarks().map { b ->
            BackupBookmark(
                novelUrl = b.novelUrl,
                chapterIndex = b.chapterIndex,
                chapterTitle = b.chapterTitle,
                snippet = b.snippet,
                scrollPosition = b.scrollPosition,
                createdAt = b.createdAt
            )
        }

        val allStats = novelDao.getAllReadingStats().map { s ->
            BackupReadingStat(
                novelUrl = s.novelUrl,
                totalReadingTimeMinutes = s.totalReadingTimeMinutes,
                chaptersCompleted = s.chaptersCompleted,
                lastSessionDate = s.lastSessionDate
            )
        }

        val appPrefs = AppPreferences(context)
        val backupPrefs = BackupPreferences(
            readerFontSize = appPrefs.readerFontSize,
            readerLineHeight = appPrefs.readerLineHeight,
            readerHorizontalPadding = appPrefs.readerHorizontalPadding,
            readerVerticalPadding = appPrefs.readerVerticalPadding,
            readerFontMode = appPrefs.readerFontMode,
            readerThemeMode = appPrefs.readerThemeMode,
            keepScreenOn = appPrefs.keepScreenOn,
            autoScrollSpeed = appPrefs.autoScrollSpeed,
            autoUpdateEnabled = UpdatePreferences.isAutoUpdateEnabled(context)
        )

        val customParserJsons = mutableListOf<String>()
        try {
            val customDir = File(context.filesDir, "custom_parsers")
            if (customDir.exists()) {
                customDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".json")) {
                        customParserJsons.add(file.readText())
                    }
                }
            }
        } catch (_: Exception) {
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDate = sdf.format(Date())

        LibraryBackup(
            version = 1,
            appName = "LN lecteurFR",
            exportedAtTimestamp = System.currentTimeMillis(),
            exportedAtFormatted = formattedDate,
            totalNovelsCount = backupNovels.size,
            novels = backupNovels,
            bookmarks = allBookmarks,
            readingStats = allStats,
            preferences = backupPrefs,
            customParsers = customParserJsons
        )
    }

    /**
     * Génère la chaîne de caractères JSON représentant la sauvegarde complète.
     */
    suspend fun exportBackupToJsonString(context: Context): String = withContext(Dispatchers.IO) {
        val backup = createLibraryBackup(context)
        json.encodeToString(backup)
    }

    /**
     * Écrit la sauvegarde dans un fichier temporaire dans le cache prêt pour le partage via Intent.
     */
    suspend fun exportBackupToCacheFile(context: Context): File = withContext(Dispatchers.IO) {
        val jsonString = exportBackupToJsonString(context)
        val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
        val fileName = "ln_lecteurfr_backup_${fileDateFormat.format(Date())}.json"
        
        val file = File(context.cacheDir, fileName)
        file.writeText(jsonString)
        file
    }

    /**
     * Valide le contenu JSON d'un fichier de sauvegarde et retourne un aperçu.
     */
    fun validateBackupJson(jsonString: String): BackupValidationResult {
        return try {
            val backup = json.decodeFromString<LibraryBackup>(jsonString)
            if (backup.novels.isEmpty() && backup.preferences == null && backup.bookmarks.isEmpty() && backup.readingStats.isEmpty()) {
                BackupValidationResult(
                    isValid = false,
                    errorMessage = "Le fichier JSON ne contient aucune donnée de bibliothèque valide."
                )
            } else {
                BackupValidationResult(
                    isValid = true,
                    backup = backup,
                    novelsCount = backup.novels.size,
                    bookmarksCount = backup.bookmarks.size,
                    readingStatsCount = backup.readingStats.size,
                    exportedDateFormatted = backup.exportedAtFormatted.ifBlank { "Inconnue" }
                )
            }
        } catch (e: Exception) {
            BackupValidationResult(
                isValid = false,
                errorMessage = "Format de fichier non valide : ${e.localizedMessage ?: "Erreur de syntaxe JSON"}"
            )
        }
    }

    /**
     * Restaure l'ensemble de la bibliothèque et des réglages depuis un objet LibraryBackup.
     */
    suspend fun restoreLibrary(context: Context, backup: LibraryBackup): RestoreResult = withContext(Dispatchers.IO) {
        try {
            var restoredNovels = 0
            val restoredNovelUrls = mutableListOf<String>()
            var restoredBookmarks = 0

            // 1. Restaurer les romans
            for (bn in backup.novels) {
                val existing = novelDao.getSavedNovelByUrl(bn.originalUrl)
                val targetNovel = if (existing != null) {
                    // Mettre à jour en conservant la progression la plus avancée
                    existing.copy(
                        title = bn.title.ifBlank { existing.title },
                        author = bn.author.ifBlank { existing.author },
                        coverUrl = bn.coverUrl.ifBlank { existing.coverUrl },
                        synopsis = bn.synopsis.ifBlank { existing.synopsis },
                        genres = bn.genres.ifBlank { existing.genres },
                        totalChapters = maxOf(existing.totalChapters, bn.totalChapters),
                        lastReadChapterIndex = maxOf(existing.lastReadChapterIndex, bn.lastReadChapterIndex),
                        lastReadPosition = if (bn.lastReadChapterIndex >= existing.lastReadChapterIndex) bn.lastReadPosition else existing.lastReadPosition,
                        category = if (bn.category.isNotBlank() && bn.category != "Général") bn.category else existing.category,
                        isFavorite = existing.isFavorite || bn.isFavorite,
                        exportMode = bn.exportMode.ifBlank { existing.exportMode },
                        lastReadAt = maxOf(existing.lastReadAt, bn.lastReadAt)
                    )
                } else {
                    SavedNovelEntity(
                        originalUrl = bn.originalUrl,
                        title = bn.title,
                        author = bn.author,
                        coverUrl = bn.coverUrl,
                        synopsis = bn.synopsis,
                        genres = bn.genres,
                        totalChapters = bn.totalChapters,
                        lastReadChapterIndex = bn.lastReadChapterIndex,
                        lastReadPosition = bn.lastReadPosition,
                        category = bn.category,
                        isFavorite = bn.isFavorite,
                        exportMode = bn.exportMode,
                        epubFilePath = bn.epubFilePath,
                        addedAt = if (bn.addedAt > 0) bn.addedAt else System.currentTimeMillis(),
                        lastReadAt = bn.lastReadAt
                    )
                }
                novelDao.insertSavedNovel(targetNovel)

                // Restaurer les chapitres
                if (bn.chapters.isNotEmpty()) {
                    val existingChapters = novelDao.getChaptersForNovel(bn.originalUrl).associateBy { it.chapterIndex }
                    val chapterEntities = bn.chapters.map { ch ->
                        val ex = existingChapters[ch.chapterIndex]
                        ChapterEntity(
                            novelUrl = bn.originalUrl,
                            chapterIndex = ch.chapterIndex,
                            title = ch.title,
                            url = ch.url,
                            content = ex?.content ?: "",
                            isDownloaded = ex?.isDownloaded ?: ch.isDownloaded,
                            filePath = ex?.filePath ?: ch.filePath
                        )
                    }
                    novelDao.insertChapters(chapterEntities)
                }

                restoredNovels++
                restoredNovelUrls.add(targetNovel.originalUrl)
            }

            // 2. Restaurer les signets
            if (backup.bookmarks.isNotEmpty()) {
                val bookmarkEntities = backup.bookmarks.map { b ->
                    BookmarkEntity(
                        novelUrl = b.novelUrl,
                        chapterIndex = b.chapterIndex,
                        chapterTitle = b.chapterTitle,
                        snippet = b.snippet,
                        scrollPosition = b.scrollPosition,
                        createdAt = if (b.createdAt > 0) b.createdAt else System.currentTimeMillis()
                    )
                }
                novelDao.insertBookmarks(bookmarkEntities)
                restoredBookmarks = bookmarkEntities.size
            }

            // 3. Restaurer les statistiques de lecture
            var restoredStats = 0
            if (backup.readingStats.isNotEmpty()) {
                val statEntities = backup.readingStats.map { s ->
                    val existing = novelDao.getReadingStats(s.novelUrl)
                    ReadingStatEntity(
                        novelUrl = s.novelUrl,
                        totalReadingTimeMinutes = maxOf(existing?.totalReadingTimeMinutes ?: 0L, s.totalReadingTimeMinutes),
                        chaptersCompleted = maxOf(existing?.chaptersCompleted ?: 0, s.chaptersCompleted),
                        lastSessionDate = maxOf(existing?.lastSessionDate ?: 0L, s.lastSessionDate)
                    )
                }
                novelDao.insertReadingStats(statEntities)
                restoredStats = statEntities.size
            }

            // 4. Restaurer les préférences de lecture
            backup.preferences?.let { p ->
                val appPrefs = AppPreferences(context)
                appPrefs.readerFontSize = p.readerFontSize
                appPrefs.readerLineHeight = p.readerLineHeight
                appPrefs.readerHorizontalPadding = p.readerHorizontalPadding
                appPrefs.readerVerticalPadding = p.readerVerticalPadding
                appPrefs.readerFontMode = p.readerFontMode
                appPrefs.readerThemeMode = p.readerThemeMode
                appPrefs.keepScreenOn = p.keepScreenOn
                appPrefs.autoScrollSpeed = p.autoScrollSpeed
                UpdatePreferences.setAutoUpdateEnabled(context, p.autoUpdateEnabled)
            }

            // 5. Restaurer les parseurs personnalisés éventuels
            if (backup.customParsers.isNotEmpty()) {
                try {
                    val customDir = File(context.filesDir, "custom_parsers")
                    if (!customDir.exists()) customDir.mkdirs()
                    for (parserJson in backup.customParsers) {
                        try {
                            DynamicParserManager.importParser(context, parserJson)
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {
                }
            }

            RestoreResult(
                success = true,
                restoredNovelsCount = restoredNovels,
                restoredBookmarksCount = restoredBookmarks,
                restoredStatsCount = restoredStats,
                restoredNovelUrls = restoredNovelUrls,
                message = "Restauration terminée avec succès !"
            )
        } catch (e: Exception) {
            RestoreResult(
                success = false,
                message = "Erreur pendant la restauration : ${e.localizedMessage ?: e.message}"
            )
        }
    }
}
