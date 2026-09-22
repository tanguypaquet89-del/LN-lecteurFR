package com.nahrahviing.lecteurnovel.data.network.parsers.dynamic

import com.nahrahviing.lecteurnovel.data.model.Chapter
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import com.nahrahviing.lecteurnovel.data.network.parsers.SiteParser
import com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class DynamicNovelParser(
    val config: DynamicParserConfig
) : SiteParser {

    override val name: String get() = config.name
    override val baseUrl: String get() = config.baseUrl

    override fun canParse(url: String): Boolean {
        val lower = url.lowercase()
        return config.urlPatterns.any { lower.contains(it.lowercase()) }
    }

    override fun isNovelUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (!canParse(lower)) return false

        // Vérifier les exclusions
        for (exclude in config.novelUrlExcludePatterns) {
            try {
                if (Regex(exclude, RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
                    return false
                }
            } catch (_: Exception) {
                if (lower.contains(exclude.lowercase())) return false
            }
        }

        // Si des patterns spécifiques sont définis, vérifier correspondance
        if (config.novelUrlPatterns.isNotEmpty()) {
            return config.novelUrlPatterns.any { pattern ->
                try {
                    Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(lower) || lower.contains(pattern.lowercase())
                } catch (_: Exception) {
                    lower.contains(pattern.lowercase())
                }
            }
        }

        return true
    }

    override suspend fun extractNovel(url: String, html: String?): NovelInfo? = withContext(Dispatchers.IO) {
        try {
            val doc = if (html != null && html.isNotBlank()) {
                Jsoup.parse(html, url)
            } else {
                val conn = Jsoup.connect(url)
                    .userAgent(config.userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .timeout(15000)
                config.headers.forEach { (k, v) -> conn.header(k, v) }
                conn.get()
            }

            // Titre
            val title = doc.select(config.titleSelector).firstOrNull()?.text()?.trim()
                ?: doc.select("meta[property='og:title']").firstOrNull()?.attr("content")?.trim()
                ?: "Roman sans titre"

            // Auteur
            var author = config.authorDefault
            config.authorSelector?.let { sel ->
                doc.select(sel).firstOrNull()?.let { el ->
                    val authorText = el.text().trim()
                    if (authorText.isNotBlank()) author = authorText
                }
            }

            // Couverture
            var coverUrl = ""
            config.coverSelector?.let { sel ->
                doc.select(sel).firstOrNull()?.let { el ->
                    val src = el.absUrl(config.coverAttr).ifBlank { el.attr(config.coverAttr) }
                    coverUrl = if (src.isNotBlank()) src else {
                        el.absUrl(config.coverFallbackAttr).ifBlank { el.attr(config.coverFallbackAttr) }
                    }
                }
            }
            if (coverUrl.isBlank()) {
                coverUrl = doc.select("meta[property='og:image']").firstOrNull()?.attr("content") ?: ""
            }

            // Synopsis
            var synopsis = ""
            config.synopsisSelector?.let { sel ->
                val synopsisElements = doc.select(sel)
                if (synopsisElements.isNotEmpty()) {
                    synopsis = synopsisElements.joinToString("\n\n") { it.text().trim() }.trim()
                }
            }
            if (synopsis.isBlank()) {
                synopsis = doc.select("meta[name='description']").firstOrNull()?.attr("content") ?: ""
            }

            // Genres
            val genres = mutableListOf<String>()
            config.genresSelector?.let { sel ->
                doc.select(sel).forEach { el ->
                    val g = el.text().trim()
                    if (g.isNotBlank() && !genres.contains(g)) genres.add(g)
                }
            }

            // Chapitres
            val chapters = mutableListOf<Chapter>()
            val chapterElements = doc.select(config.chapterListSelector)

            chapterElements.forEach { el ->
                val chapUrl = el.absUrl(config.chapterUrlAttr).ifBlank { el.attr(config.chapterUrlAttr) }
                val chapTitle = config.chapterTitleSelector?.let { sel ->
                    el.select(sel).firstOrNull()?.text()?.trim()
                } ?: el.text().trim().ifBlank {
                    el.attr(config.chapterTitleAttr).trim()
                }

                if (chapUrl.isNotBlank() && chapTitle.isNotBlank() && !chapters.any { it.url == chapUrl }) {
                    chapters.add(Chapter(index = 0, title = chapTitle, url = chapUrl))
                }
            }

            if (config.chaptersReverse) {
                chapters.reverse()
            }

            val indexedChapters = chapters.mapIndexed { idx, chap -> chap.copy(index = idx + 1) }

            if (indexedChapters.isNotEmpty()) {
                return@withContext NovelInfo(title, author, coverUrl, synopsis, genres, indexedChapters, url)
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun fetchChapterContent(url: String, novelTitle: String): String = withContext(Dispatchers.IO) {
        try {
            val conn = Jsoup.connect(url)
                .userAgent(config.userAgent)
                .timeout(15000)
            config.headers.forEach { (k, v) -> conn.header(k, v) }
            val doc = conn.get()

            // Supprimer les éléments indésirables (pubs, scripts, styles)
            for (removeSel in config.contentRemoveSelectors) {
                doc.select(removeSel).remove()
            }

            val paragraphs = doc.select(config.contentSelector)
            val contentBuilder = StringBuilder()
            for (p in paragraphs) {
                val text = p.text().trim()
                if (text.isNotBlank()) {
                    contentBuilder.append(text).append("\n\n")
                }
            }

            val raw = contentBuilder.toString()
            if (config.contentCleanTitle) {
                NovelContentCleaner.cleanToPlainText(raw, novelTitle)
            } else {
                raw
            }
        } catch (e: Exception) {
            "Erreur lors de la récupération du chapitre: ${e.localizedMessage}"
        }
    }
}
