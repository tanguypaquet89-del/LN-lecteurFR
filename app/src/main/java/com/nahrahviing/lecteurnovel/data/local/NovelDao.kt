package com.nahrahviing.lecteurnovel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {
    @Query("SELECT * FROM saved_novels ORDER BY lastReadAt DESC")
    fun getAllSavedNovels(): Flow<List<SavedNovelEntity>>

    @Query("SELECT * FROM saved_novels ORDER BY lastReadAt DESC")
    suspend fun getAllSavedNovelsList(): List<SavedNovelEntity>

    @Query("UPDATE saved_novels SET totalChapters = :total WHERE originalUrl = :novelUrl")
    suspend fun updateTotalChapters(novelUrl: String, total: Int)

    @Query("UPDATE saved_novels SET totalChapters = :total, epubFilePath = :epubPath WHERE originalUrl = :novelUrl")
    suspend fun updateTotalChaptersAndEpub(novelUrl: String, total: Int, epubPath: String?)

    @Query("UPDATE saved_novels SET exportMode = :mode WHERE originalUrl = :novelUrl")
    suspend fun updateExportMode(novelUrl: String, mode: String)

    @Query("SELECT * FROM saved_novels WHERE originalUrl = :url LIMIT 1")
    suspend fun getSavedNovelByUrl(url: String): SavedNovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedNovel(novel: SavedNovelEntity)

    @Update
    suspend fun updateSavedNovel(novel: SavedNovelEntity)

    @Query("DELETE FROM saved_novels WHERE originalUrl = :url")
    suspend fun deleteNovel(url: String)

    @Query("DELETE FROM chapters WHERE novelUrl = :novelUrl")
    suspend fun deleteChaptersForNovel(novelUrl: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Query("UPDATE chapters SET content = :content, isDownloaded = :isDownloaded WHERE novelUrl = :novelUrl AND chapterIndex = :index")
    suspend fun updateChapterContent(novelUrl: String, index: Int, content: String, isDownloaded: Boolean = true)


    @Query("SELECT * FROM chapters WHERE novelUrl = :novelUrl ORDER BY chapterIndex ASC")
    suspend fun getChaptersForNovel(novelUrl: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE novelUrl = :novelUrl AND chapterIndex = :index LIMIT 1")
    suspend fun getChapter(novelUrl: String, index: Int): ChapterEntity?

    @Query("SELECT COUNT(*) FROM chapters WHERE novelUrl = :novelUrl")
    suspend fun getDownloadedChaptersCount(novelUrl: String): Int

    @Query("UPDATE saved_novels SET lastReadChapterIndex = :index, lastReadPosition = :pos, lastReadAt = :timestamp WHERE originalUrl = :novelUrl")
    suspend fun updateReadingProgress(novelUrl: String, index: Int, pos: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE saved_novels SET isFavorite = :fav WHERE originalUrl = :novelUrl")
    suspend fun updateFavorite(novelUrl: String, fav: Boolean)

    @Query("UPDATE saved_novels SET category = :cat WHERE originalUrl = :novelUrl")
    suspend fun updateCategory(novelUrl: String, cat: String)

    // Signets
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(bookmarks: List<BookmarkEntity>)

    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE novelUrl = :novelUrl ORDER BY createdAt DESC")
    fun getBookmarksForNovel(novelUrl: String): Flow<List<BookmarkEntity>>

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    // Stats
    @Query("SELECT * FROM reading_stats WHERE novelUrl = :novelUrl LIMIT 1")
    suspend fun getReadingStats(novelUrl: String): ReadingStatEntity?

    @Query("SELECT * FROM reading_stats")
    suspend fun getAllReadingStats(): List<ReadingStatEntity>

    @Query("SELECT * FROM reading_stats")
    fun getAllReadingStatsFlow(): Flow<List<ReadingStatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateReadingStats(stat: ReadingStatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingStats(stats: List<ReadingStatEntity>)
}
