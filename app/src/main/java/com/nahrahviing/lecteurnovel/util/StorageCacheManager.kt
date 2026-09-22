package com.nahrahviing.lecteurnovel.util

import android.content.Context
import java.io.File

data class StorageSummary(
    val downloadedNovelsCount: Int,
    val epubFilesSizeMB: Double,
    val cacheSizeMB: Double,
    val totalAppStorageMB: Double
)

object StorageCacheManager {
    fun getStorageSummary(context: Context, novelsCount: Int): StorageSummary {
        val formatsDirs = listOf("epubs", "pdfs", "odts", "txts")
        var filesSize = 0L
        for (dirName in formatsDirs) {
            val dir = File(context.getExternalFilesDir(null), dirName)
            filesSize += getFolderSize(dir)
        }
        val cacheSize = getFolderSize(context.cacheDir)
        val totalSize = filesSize + cacheSize

        return StorageSummary(
            downloadedNovelsCount = novelsCount,
            epubFilesSizeMB = filesSize / (1024.0 * 1024.0),
            cacheSizeMB = cacheSize / (1024.0 * 1024.0),
            totalAppStorageMB = totalSize / (1024.0 * 1024.0)
        )
    }

    fun clearCache(context: Context) {
        try {
            context.cacheDir.deleteRecursively()
        } catch (_: Exception) {}
    }

    private fun getFolderSize(folder: File?): Long {
        if (folder == null || !folder.exists()) return 0L
        var length: Long = 0
        folder.listFiles()?.forEach { file ->
            length += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return length
    }
}
