package com.nahrahviing.lecteurnovel.data.network.parsers

import com.nahrahviing.lecteurnovel.data.model.Chapter
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class SoreyawariParser : SiteParser {
    override val name = "Soreyawari"
    override val baseUrl = "https://soreyawari.com"

    override fun canParse(url: String): Boolean {
        return url.lowercase().contains("soreyawari.com")
    }

    override fun isNovelUrl(url: String): Boolean {
        val clean = url.lowercase().trimEnd('/')
        return clean.contains("soreyawari.com") && clean.contains("/roman/") && !clean.contains("/chapitre/")
    }

    override suspend fun extractNovel(url: String, html: String?): NovelInfo? = withContext(Dispatchers.IO) {
        try {
            val doc = if (html != null) Jsoup.parse(html, url) else Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(15000).get()
            val title = doc.select(".elementor-heading-title, h1").firstOrNull()?.text()?.trim() ?: "Roman Soreyawari"
            val author = "Soreyawari"
            val coverUrl = doc.select("img[src*='uploads']").firstOrNull()?.absUrl("src") ?: ""
            val synopsis = doc.select(".elementor-text-editor p").firstOrNull()?.text()?.trim() ?: ""
            val genres = listOf("Traduction")
            
            val chapters = mutableListOf<Chapter>()
            val chapterLinks = doc.select("a[href*='soreyawari.com/20']")
            chapterLinks.forEach { el ->
                val chapUrl = el.absUrl("href")
                val chapTitle = el.text().trim()
                if (chapUrl.isNotEmpty() && chapTitle.isNotEmpty() && !chapters.any { it.url == chapUrl }) {
                    chapters.add(Chapter(index = 0, title = chapTitle, url = chapUrl))
                }
            }
            
            // Fix index
            val finalChapters = chapters.mapIndexed { idx, chap -> chap.copy(index = idx + 1) }

            if (finalChapters.isNotEmpty()) {
                return@withContext NovelInfo(title, author, coverUrl, synopsis, genres, finalChapters, url)
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun fetchChapterContent(url: String, novelTitle: String): String = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(15000).get()
            val textBlocks = doc.select(".elementor-widget-theme-post-content p, .entry-content p")
            val contentStr = StringBuilder()
            for (p in textBlocks) {
                val pText = p.text().trim()
                if (pText.isNotBlank()) contentStr.append(pText).append("\n\n")
            }
            com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner.cleanToPlainText(contentStr.toString(), novelTitle)
        } catch (e: Exception) {
            "Erreur lors de la récupération : ${e.message}"
        }
    }
}
