package com.nahrahviing.lecteurnovel.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class LibraryBackup(
    val version: Int = 1,
    val appName: String = "LN lecteurFR",
    val exportedAtTimestamp: Long = System.currentTimeMillis(),
    val exportedAtFormatted: String = "",
    val totalNovelsCount: Int = 0,
    val novels: List<BackupNovel> = emptyList(),
    val bookmarks: List<BackupBookmark> = emptyList(),
    val readingStats: List<BackupReadingStat> = emptyList(),
    val preferences: BackupPreferences? = null,
    val customParsers: List<String> = emptyList()
)

@Serializable
data class BackupNovel(
    val originalUrl: String,
    val title: String,
    val author: String,
    val coverUrl: String = "",
    val synopsis: String = "",
    val genres: String = "",
    val totalChapters: Int = 0,
    val lastReadChapterIndex: Int = 0,
    val lastReadPosition: Int = 0,
    val category: String = "Général",
    val isFavorite: Boolean = false,
    val exportMode: String = "SINGLE_EPUB",
    val epubFilePath: String? = null,
    val addedAt: Long = 0L,
    val lastReadAt: Long = 0L,
    val chapters: List<BackupChapter> = emptyList()
)

@Serializable
data class BackupChapter(
    val chapterIndex: Int,
    val title: String,
    val url: String,
    val isDownloaded: Boolean = false,
    val filePath: String? = null
)

@Serializable
data class BackupBookmark(
    val novelUrl: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val snippet: String,
    val scrollPosition: Int,
    val createdAt: Long = 0L
)

@Serializable
data class BackupReadingStat(
    val novelUrl: String,
    val totalReadingTimeMinutes: Long = 0L,
    val chaptersCompleted: Int = 0,
    val totalWordsRead: Long = 0L,
    val lastSessionDate: Long = 0L
)

@Serializable
data class BackupPreferences(
    val readerFontSize: Float = 17f,
    val readerLineHeight: Float = 1.6f,
    val readerHorizontalPadding: Int = 16,
    val readerVerticalPadding: Int = 12,
    val readerFontMode: String = "SYSTEM",
    val readerThemeMode: String = "SYSTEM",
    val keepScreenOn: Boolean = true,
    val autoScrollSpeed: Int = 3,
    val autoUpdateEnabled: Boolean = true
)

data class BackupValidationResult(
    val isValid: Boolean,
    val backup: LibraryBackup? = null,
    val errorMessage: String? = null,
    val novelsCount: Int = 0,
    val bookmarksCount: Int = 0,
    val readingStatsCount: Int = 0,
    val exportedDateFormatted: String = ""
)

data class RestoreResult(
    val success: Boolean,
    val restoredNovelsCount: Int = 0,
    val restoredBookmarksCount: Int = 0,
    val restoredStatsCount: Int = 0,
    val restoredNovelUrls: List<String> = emptyList(),
    val message: String
)
