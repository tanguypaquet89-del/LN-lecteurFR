package com.nahrahviing.lecteurnovel.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelUrl: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val snippet: String,
    val scrollPosition: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reading_stats")
data class ReadingStatEntity(
    @PrimaryKey val novelUrl: String,
    val totalReadingTimeMinutes: Long = 0,
    val chaptersCompleted: Int = 0,
    val lastSessionDate: Long = System.currentTimeMillis()
)
