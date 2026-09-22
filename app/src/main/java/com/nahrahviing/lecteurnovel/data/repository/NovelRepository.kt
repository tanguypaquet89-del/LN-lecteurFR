package com.nahrahviing.lecteurnovel.data.repository

import android.content.Context
import com.nahrahviing.lecteurnovel.data.epub.EpubGenerator
import com.nahrahviing.lecteurnovel.data.epub.ExportFormat
import com.nahrahviing.lecteurnovel.data.epub.NovelExporter
import com.nahrahviing.lecteurnovel.data.local.AppDatabase
import com.nahrahviing.lecteurnovel.data.local.BookmarkEntity
import com.nahrahviing.lecteurnovel.data.local.ChapterEntity
import com.nahrahviing.lecteurnovel.data.local.ReadingStatEntity
import com.nahrahviing.lecteurnovel.data.local.SavedNovelEntity
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import com.nahrahviing.lecteurnovel.data.network.parsers.ParserManager
import com.nahrahviing.lecteurnovel.util.AppPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * Dépôt central de données (Repository) de l'application LecteurNovel.
 * 
 * Responsabilités principales :
 * - Gestion de la persistance locale (Room Database) pour les romans enregistrés, chapitres, marque-pages et statistiques de lecture.
 * - Orchestration des téléchargements de chapitres avec temporisation anti-rate-limit et reprises automatiques.
 * - Exportation des romans en formats standards (EPUB, PDF, ODT, TXT).
 * - Synchronisation et mise à jour incrémentale des nouveaux chapitres parus en ligne.
 */
class NovelRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val novelDao = db.novelDao()

    fun getAllSavedNovels(): Flow<List<SavedNovelEntity>> = novelDao.getAllSavedNovels()

    suspend fun getAllSavedNovelsSync(): List<SavedNovelEntity> = novelDao.getAllSavedNovelsList()

    suspend fun insertNewChapters(newChapters: List<ChapterEntity>, newTotalChapters: Int, novelUrl: String) {
        novelDao.insertChapters(newChapters)
        novelDao.updateTotalChapters(novelUrl, newTotalChapters)
    }

    suspend fun getSavedNovelByUrl(url: String): SavedNovelEntity? = novelDao.getSavedNovelByUrl(url)

    /**
     * Sauvegarde ou met à jour un roman complet et ses chapitres en base de données locale (Room).
     * Préserve le contenu des chapitres déjà téléchargés lors d'une simple mise à jour des métadonnées
     * ou d'un rechargement de la liste de chapitres en ligne.
     */
    suspend fun saveNovelWithChapters(
        novel: NovelInfo,
        epubPath: String? = null,
        exportMode: String = "SINGLE_EPUB",
        chapterFilePaths: Map<Int, String> = emptyMap()
    ) {
        val existing = novelDao.getSavedNovelByUrl(novel.originalUrl)
        val entity = SavedNovelEntity(
            originalUrl = novel.originalUrl,
            title = novel.title,
            author = novel.author,
            coverUrl = novel.coverUrl,
            synopsis = novel.synopsis,
            genres = novel.genres.joinToString(", "),
            totalChapters = novel.chapters.size,
            lastReadChapterIndex = existing?.lastReadChapterIndex ?: 0,
            lastReadPosition = existing?.lastReadPosition ?: 0,
            category = existing?.category ?: "Général",
            isFavorite = existing?.isFavorite ?: false,
            epubFilePath = epubPath ?: existing?.epubFilePath,
            exportMode = exportMode,
            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
            lastReadAt = existing?.lastReadAt ?: System.currentTimeMillis()
        )
        novelDao.insertSavedNovel(entity)
        
        // Récupérer les chapitres existants pour préserver le contenu textuel et le statut téléchargé
        val existingChaptersMap = novelDao.getChaptersForNovel(novel.originalUrl).associateBy { it.chapterIndex }

        // Supprimer les anciens enregistrements pour réinsérer la séquence propre et ordonnée
        novelDao.deleteChaptersForNovel(novel.originalUrl)

        val chapterEntities = novel.chapters.map { chap ->
            val prev = existingChaptersMap[chap.index]
            val contentToSave = if (chap.content.isNotBlank()) chap.content else (prev?.content ?: "")
            val isDownloaded = contentToSave.isNotBlank()
            val finalFilePath = chapterFilePaths[chap.index] ?: prev?.filePath
            ChapterEntity(
                novelUrl = novel.originalUrl,
                chapterIndex = chap.index,
                title = chap.title,
                url = chap.url,
                content = contentToSave,
                isDownloaded = isDownloaded,
                filePath = finalFilePath
            )
        }
        novelDao.insertChapters(chapterEntities)
    }

    suspend fun getChaptersForNovel(novelUrl: String): List<ChapterEntity> =
        novelDao.getChaptersForNovel(novelUrl)

    suspend fun getChapter(novelUrl: String, index: Int): ChapterEntity? =
        novelDao.getChapter(novelUrl, index)

    suspend fun updateChapterContent(novelUrl: String, index: Int, content: String, isDownloaded: Boolean = true) {
        novelDao.updateChapterContent(novelUrl, index, content, isDownloaded)
    }

    suspend fun addNovelToLibraryWithoutDownload(novel: NovelInfo) {
        saveNovelWithChapters(
            novel = novel,
            epubPath = null,
            exportMode = "ONLINE_STREAM",
            chapterFilePaths = emptyMap()
        )
    }


    suspend fun deleteNovel(novelUrl: String) {
        novelDao.deleteNovel(novelUrl)
        novelDao.deleteChaptersForNovel(novelUrl)
    }

    suspend fun toggleFavorite(novelUrl: String, isFav: Boolean) {
        novelDao.updateFavorite(novelUrl, isFav)
    }

    suspend fun updateCategory(novelUrl: String, category: String) {
        novelDao.updateCategory(novelUrl, category)
    }

    suspend fun updateProgress(novelUrl: String, chapterIndex: Int, pos: Int) {
        novelDao.updateReadingProgress(novelUrl, chapterIndex, pos)
    }

    // Signets
    fun getBookmarks(novelUrl: String): Flow<List<BookmarkEntity>> =
        novelDao.getBookmarksForNovel(novelUrl)

    suspend fun addBookmark(novelUrl: String, chapterIndex: Int, title: String, snippet: String, pos: Int) {
        novelDao.insertBookmark(
            BookmarkEntity(
                novelUrl = novelUrl,
                chapterIndex = chapterIndex,
                chapterTitle = title,
                snippet = snippet,
                scrollPosition = pos
            )
        )
    }

    suspend fun deleteBookmark(id: Long) = novelDao.deleteBookmark(id)

    // Stats
    suspend fun getReadingStats(novelUrl: String): ReadingStatEntity? =
        novelDao.getReadingStats(novelUrl)

    fun getAllReadingStatsFlow(): Flow<List<ReadingStatEntity>> =
        novelDao.getAllReadingStatsFlow()

    suspend fun getAllReadingStats(): List<ReadingStatEntity> =
        novelDao.getAllReadingStats()

    suspend fun recordReadingSession(novelUrl: String, minutes: Long) {
        val current = novelDao.getReadingStats(novelUrl) ?: ReadingStatEntity(novelUrl = novelUrl)
        val updated = current.copy(
            totalReadingTimeMinutes = current.totalReadingTimeMinutes + minutes,
            lastSessionDate = System.currentTimeMillis()
        )
        novelDao.insertOrUpdateReadingStats(updated)
    }

    suspend fun incrementCompletedChapter(novelUrl: String) {
        val current = novelDao.getReadingStats(novelUrl) ?: ReadingStatEntity(novelUrl = novelUrl)
        val updated = current.copy(
            chaptersCompleted = current.chaptersCompleted + 1,
            lastSessionDate = System.currentTimeMillis()
        )
        novelDao.insertOrUpdateReadingStats(updated)
    }

    suspend fun downloadNovelWithConfig(
        novel: NovelInfo,
        format: String, // "EPUB" or "TXT"
        exportMode: String, // "SINGLE_EPUB" or "SEPARATE_CHAPTERS"
        chaptersSubset: List<com.nahrahviing.lecteurnovel.data.model.Chapter>,
        onProgress: (Int, Int) -> Unit
    ): String {
        val downloadedChapters = mutableListOf<com.nahrahviing.lecteurnovel.data.model.Chapter>()
        val chapterFilePaths = mutableMapOf<Int, String>()
        val exportFormat = ExportFormat.fromString(format)

        val safeTitle = EpubGenerator.getSafeTitle(novel.title)
        val formatDirName = when (exportFormat) {
            ExportFormat.EPUB -> "epubs"
            ExportFormat.PDF -> "pdfs"
            ExportFormat.ODT -> "odts"
            ExportFormat.TXT -> "txts"
        }
        val novelDir = java.io.File(java.io.File(context.getExternalFilesDir(null), formatDirName), safeTitle)

        val prefs = AppPreferences(context)
        val delayBetweenChapters = prefs.downloadChapterDelayMs

        chaptersSubset.forEachIndexed { i, chapter ->
            kotlinx.coroutines.yield()
            onProgress(i + 1, chaptersSubset.size)

            // Récupération avec politique de reprise intelligente (jusqu'à 3 essais en cas de rate-limit ou coupure transitoire)
            var rawContent = ""
            var attempts = 0
            val maxAttempts = 3
            while (attempts < maxAttempts) {
                attempts++
                try {
                    rawContent = ParserManager.fetchChapterContent(chapter.url, novel.title)
                    if (rawContent.isNotBlank() && !rawContent.startsWith("Erreur")) {
                        break
                    }
                } catch (e: Exception) {
                    if (attempts >= maxAttempts) break
                }
                // Si échec ou blocage Cloudflare/débit, pause exponentielle progressive
                if (attempts < maxAttempts) {
                    delay(1000L * attempts)
                }
            }

            val cleanContent = com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner.cleanToPlainText(rawContent, novel.title)
            val chapWithContent = chapter.copy(content = cleanContent)
            downloadedChapters.add(chapWithContent)

            // Si l'utilisateur a choisi un fichier par chapitre
            if (exportMode == "SEPARATE_CHAPTERS") {
                val file = NovelExporter.exportSingleChapter(context, novel, chapWithContent, exportFormat)
                chapterFilePaths[chapter.index] = file.absolutePath
            }

            // Temporisation configurable anti-blocage de débit / Cloudflare
            if (delayBetweenChapters > 0L && i < chaptersSubset.size - 1) {
                delay(delayBetweenChapters)
            }
        }

        val outPath: String
        if (exportMode == "SINGLE_EPUB" || exportMode == "SINGLE_FILE") {
            val partialNovel = novel.copy(chapters = downloadedChapters)
            val file = NovelExporter.exportNovel(context, partialNovel, exportFormat)
            outPath = file.absolutePath
        } else {
            outPath = novelDir.absolutePath
        }

        // Merge downloaded chapters back into the full novel for DB
        val allChaptersMerged = novel.chapters.map { originalChap ->
            downloadedChapters.find { it.url == originalChap.url } ?: originalChap
        }
        val completeNovelToSave = novel.copy(chapters = allChaptersMerged)
        saveNovelWithChapters(
            novel = completeNovelToSave,
            epubPath = outPath,
            exportMode = exportMode,
            chapterFilePaths = chapterFilePaths
        )

        return outPath
    }

    suspend fun syncNewChaptersForNovel(
        savedNovel: SavedNovelEntity,
        newChapters: List<com.nahrahviing.lecteurnovel.data.model.Chapter>
    ): Int {
        if (newChapters.isEmpty()) return 0

        val newEntities = mutableListOf<ChapterEntity>()
        val downloadedNewChapters = mutableListOf<com.nahrahviing.lecteurnovel.data.model.Chapter>()
        val safeTitle = EpubGenerator.getSafeTitle(savedNovel.title)

        val prefs = AppPreferences(context)
        val delayBetweenChapters = prefs.downloadChapterDelayMs

        for ((idx, chap) in newChapters.withIndex()) {
            var rawContent = ""
            var attempts = 0
            val maxAttempts = 3
            while (attempts < maxAttempts) {
                attempts++
                try {
                    rawContent = ParserManager.fetchChapterContent(chap.url, savedNovel.title)
                    if (rawContent.isNotBlank() && !rawContent.startsWith("Erreur")) {
                        break
                    }
                } catch (e: Exception) {
                    if (attempts >= maxAttempts) break
                }
                if (attempts < maxAttempts) {
                    delay(1000L * attempts)
                }
            }

            val cleanContent = com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner.cleanToPlainText(rawContent, savedNovel.title)
            val downloadedChap = chap.copy(content = cleanContent)
            downloadedNewChapters.add(downloadedChap)

            var singleFilePath: String? = null
            if (savedNovel.exportMode == "SEPARATE_CHAPTERS") {
                val dummyNovel = NovelInfo(
                    title = savedNovel.title,
                    author = savedNovel.author,
                    coverUrl = savedNovel.coverUrl,
                    synopsis = savedNovel.synopsis,
                    genres = savedNovel.genres.split(", ").filter { it.isNotBlank() },
                    chapters = listOf(downloadedChap),
                    originalUrl = savedNovel.originalUrl
                )
                val file = EpubGenerator.generateSingleChapterEpub(context, dummyNovel, downloadedChap)
                singleFilePath = file.absolutePath
            }

            newEntities.add(
                ChapterEntity(
                    novelUrl = savedNovel.originalUrl,
                    chapterIndex = chap.index,
                    title = chap.title,
                    url = chap.url,
                    content = cleanContent,
                    isDownloaded = cleanContent.isNotBlank(),
                    filePath = singleFilePath
                )
            )

            if (delayBetweenChapters > 0L && idx < newChapters.size - 1) {
                delay(delayBetweenChapters)
            }
        }

        // Insérer les nouveaux chapitres en base
        novelDao.insertChapters(newEntities)
        val newTotal = savedNovel.totalChapters + newChapters.size

        if (savedNovel.exportMode == "SINGLE_EPUB") {
            // S'il a choisi un seul epub, le nouveau chapitre doit être intégré dans cet epub !
            val allChapters = novelDao.getChaptersForNovel(savedNovel.originalUrl)
            val allDownloaded = allChapters.filter { it.content.isNotBlank() }.map { entity ->
                com.nahrahviing.lecteurnovel.data.model.Chapter(
                    index = entity.chapterIndex,
                    title = entity.title,
                    url = entity.url,
                    content = entity.content
                )
            }
            val completeNovel = NovelInfo(
                title = savedNovel.title,
                author = savedNovel.author,
                coverUrl = savedNovel.coverUrl,
                synopsis = savedNovel.synopsis,
                genres = savedNovel.genres.split(", ").filter { it.isNotBlank() },
                chapters = allDownloaded,
                originalUrl = savedNovel.originalUrl
            )

            val destFile = savedNovel.epubFilePath?.let { java.io.File(it) }
            val updatedEpub = EpubGenerator.generateEpub(context, completeNovel, destinationFile = destFile)
            novelDao.updateTotalChaptersAndEpub(savedNovel.originalUrl, newTotal, updatedEpub.absolutePath)
        } else {
            novelDao.updateTotalChapters(savedNovel.originalUrl, newTotal)
        }

        return newChapters.size
    }

    suspend fun downloadNovelWithFormat(
        novel: NovelInfo,
        format: String, // "EPUB" or "TXT"
        chaptersSubset: List<com.nahrahviing.lecteurnovel.data.model.Chapter>,
        onProgress: (Int, Int) -> Unit
    ): String {
        return downloadNovelWithConfig(
            novel = novel,
            format = format,
            exportMode = "SINGLE_EPUB",
            chaptersSubset = chaptersSubset,
            onProgress = onProgress
        )
    }

    suspend fun downloadAndGenerateEpub(
        novel: NovelInfo,
        onProgress: (Int, Int) -> Unit
    ): String {
        return downloadNovelWithConfig(
            novel = novel,
            format = "EPUB",
            exportMode = "SINGLE_EPUB",
            chaptersSubset = novel.chapters,
            onProgress = onProgress
        )
    }

    suspend fun getNovelStorageSizeBytes(novel: SavedNovelEntity): Long {
        var totalBytes = 0L
        val safeTitle = EpubGenerator.getSafeTitle(novel.title)

        // 1. Fichier direct (epubFilePath)
        novel.epubFilePath?.let { path ->
            val f = java.io.File(path)
            if (f.exists()) {
                totalBytes += if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
            }
        }

        // 2. Dossiers associés dans epubs, pdfs, odts, txts
        for (sub in listOf("epubs", "pdfs", "odts", "txts")) {
            val dir = java.io.File(java.io.File(context.getExternalFilesDir(null), sub), safeTitle)
            if (dir.exists()) {
                totalBytes += dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
            val singleFile = java.io.File(java.io.File(context.getExternalFilesDir(null), sub), "$safeTitle.${sub.dropLast(1)}")
            if (singleFile.exists()) {
                totalBytes += singleFile.length()
            }
        }

        // 3. Fichiers de chapitres individuels
        val chapters = novelDao.getChaptersForNovel(novel.originalUrl)
        for (chap in chapters) {
            chap.filePath?.let { p ->
                val cf = java.io.File(p)
                if (cf.exists()) totalBytes += cf.length()
            }
        }

        return totalBytes
    }

    suspend fun deleteNovelFiles(novel: SavedNovelEntity): Long {
        var freedBytes = 0L
        val safeTitle = EpubGenerator.getSafeTitle(novel.title)

        // 1. Fichier direct
        novel.epubFilePath?.let { path ->
            val f = java.io.File(path)
            if (f.exists()) {
                val size = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
                if (f.isDirectory) f.deleteRecursively() else f.delete()
                freedBytes += size
            }
        }

        // 2. Dossiers associés dans epubs, pdfs, odts, txts
        for (sub in listOf("epubs", "pdfs", "odts", "txts")) {
            val dir = java.io.File(java.io.File(context.getExternalFilesDir(null), sub), safeTitle)
            if (dir.exists()) {
                freedBytes += dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                dir.deleteRecursively()
            }
            val singleFile = java.io.File(java.io.File(context.getExternalFilesDir(null), sub), "$safeTitle.${sub.dropLast(1)}")
            if (singleFile.exists()) {
                freedBytes += singleFile.length()
                singleFile.delete()
            }
        }

        // 3. Fichiers individuels de chapitres
        val chapters = novelDao.getChaptersForNovel(novel.originalUrl)
        for (chap in chapters) {
            chap.filePath?.let { p ->
                val cf = java.io.File(p)
                if (cf.exists()) {
                    freedBytes += cf.length()
                    cf.delete()
                }
            }
        }

        // Réinitialiser le chemin dans la BD
        novelDao.updateTotalChaptersAndEpub(novel.originalUrl, novel.totalChapters, null)

        return freedBytes
    }

    suspend fun deleteNovelAndAllFiles(novel: SavedNovelEntity): Long {
        val freedBytes = deleteNovelFiles(novel)
        deleteNovel(novel.originalUrl)
        return freedBytes
    }

    suspend fun exportSavedNovel(
        novel: SavedNovelEntity,
        format: ExportFormat,
        destinationFile: java.io.File? = null
    ): java.io.File {
        val chapters = novelDao.getChaptersForNovel(novel.originalUrl)
        val modelChapters = chapters.map { entity ->
            com.nahrahviing.lecteurnovel.data.model.Chapter(
                index = entity.chapterIndex,
                title = entity.title,
                url = entity.url,
                content = entity.content
            )
        }

        val novelInfo = NovelInfo(
            title = novel.title,
            author = novel.author,
            coverUrl = novel.coverUrl,
            synopsis = novel.synopsis,
            genres = novel.genres.split(", ").filter { it.isNotBlank() },
            chapters = modelChapters,
            originalUrl = novel.originalUrl
        )

        return NovelExporter.exportNovel(context, novelInfo, format, destinationFile)
    }
}
