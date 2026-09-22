package com.nahrahviing.lecteurnovel.data.network.parsers

import com.nahrahviing.lecteurnovel.data.model.Chapter
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class ChiReadsParser : SiteParser {
    override val name = "ChiReads"
    override val baseUrl = "https://chireads.com"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36"

    override fun canParse(url: String): Boolean {
        return url.lowercase().contains("chireads.com")
    }

    override fun isNovelUrl(url: String): Boolean {
        val clean = url.lowercase().trimEnd('/')
        if (!clean.contains("chireads.com")) return false
        // Exclude home and root categories
        if (clean == "https://chireads.com" || clean == "http://chireads.com" ||
            clean == "https://www.chireads.com" || clean == "http://www.chireads.com") return false
        if (clean.endsWith("/category/translatedtales") || clean.endsWith("/category/original")) return false
        if (clean.contains("/page/")) return false
        // ChiReads novel URLs are /category/translatedtales/<novel-slug>/ or /category/original/<novel-slug>/
        return clean.contains("/category/translatedtales/") || clean.contains("/category/original/") || clean.contains("/category/")
    }

    override suspend fun extractNovel(url: String, html: String?): NovelInfo? = withContext(Dispatchers.IO) {
        try {
            val doc = if (html != null && (html.contains("refresh-detail") || html.contains("refresh-card"))) {
                Jsoup.parse(html, url)
            } else {
                Jsoup.connect(url)
                    .userAgent(userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .timeout(15000)
                    .get()
            }

            val title = doc.select("h1.refresh-detail-title, .refresh-detail-hero h1, h1").firstOrNull()?.text()?.trim() ?: "Roman ChiReads"
            val coverUrl = doc.select(".refresh-detail-cover img").firstOrNull()?.let {
                val src = it.absUrl("src")
                if (src.isNotEmpty()) src else it.absUrl("data-src")
            } ?: ""
            val synopsis = doc.select(".refresh-detail-summary-content, .refresh-detail-hero-summary").firstOrNull()?.text()?.trim() ?: ""

            var author = "Auteur inconnu"
            val metaDivs = doc.select(".refresh-detail-meta > div")
            for (div in metaDivs) {
                val label = div.select("dt").text().trim().lowercase()
                val value = div.select("dd").text().trim()
                if (label.contains("auteur")) {
                    author = value
                }
            }

            val genres = mutableListOf<String>()
            doc.select(".refresh-detail-tags a, a[href*='/tag/']").forEach {
                val g = it.text().trim()
                if (g.isNotEmpty() && !genres.contains(g)) genres.add(g)
            }

            val chapters = mutableListOf<Chapter>()
            val chapterElements = doc.select(".refresh-detail-chapter-list a")

            chapterElements.forEachIndexed { idx, el ->
                val chapUrl = el.absUrl("href")
                var chapTitle = el.text().trim()
                if (chapTitle.isBlank()) {
                    chapTitle = el.attr("title").trim()
                }
                if (chapUrl.isNotEmpty() && !chapters.any { it.url == chapUrl }) {
                    chapters.add(Chapter(index = idx + 1, title = chapTitle, url = chapUrl))
                }
            }

            val sortedChapters = chapters.mapIndexed { idx, chap -> chap.copy(index = idx + 1) }

            if (sortedChapters.isEmpty()) return@withContext null

            NovelInfo(
                title = title,
                author = author,
                coverUrl = coverUrl,
                synopsis = synopsis,
                genres = genres,
                chapters = sortedChapters,
                originalUrl = url
            )
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun fetchChapterContent(url: String, novelTitle: String): String = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .userAgent(userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .timeout(15000)
                .get()

            val content = doc.select("#content").firstOrNull()
            if (content != null) {
                content.select("script, style, iframe, form, nav, footer, .sharedaddy, .ads, .chapter-nav").remove()
                val paragraphs = content.select("p")
                val sb = StringBuilder()
                for (p in paragraphs) {
                    val pText = p.text().trim()
                    if (pText.isNotBlank()) {
                        sb.append(pText).append("\n\n")
                    }
                }
                if (paragraphs.isNotEmpty()) {
                    return@withContext com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner.cleanToPlainText(sb.toString(), novelTitle)
                }
            }

            val textBlocks = doc.select("div.reading-content p, article p")
            val fallbackSb = StringBuilder()
            for (p in textBlocks) {
                val pText = p.text().trim()
                if (pText.isNotBlank()) {
                    fallbackSb.append(pText).append("\n\n")
                }
            }
            com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner.cleanToPlainText(fallbackSb.toString(), novelTitle)
        } catch (e: Exception) {
            "Erreur lors de la récupération : ${e.message}"
        }
    }
}
