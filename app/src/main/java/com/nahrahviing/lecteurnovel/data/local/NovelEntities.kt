package com.nahrahviing.lecteurnovel.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_novels")
data class SavedNovelEntity(
    @PrimaryKey val originalUrl: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val synopsis: String,
    val genres: String,
    val totalChapters: Int,
    val lastReadChapterIndex: Int = 0,
    val lastReadPosition: Int = 0,
    val category: String = "Général",
    val isFavorite: Boolean = false,
    val epubFilePath: String? = null,
    val exportMode: String = "SINGLE_EPUB", // "SINGLE_EPUB" or "SEPARATE_CHAPTERS"
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelUrl: String,
    val chapterIndex: Int,
    val title: String,
    val url: String,
    val content: String,
    val isDownloaded: Boolean = true,
    val filePath: String? = null
)
