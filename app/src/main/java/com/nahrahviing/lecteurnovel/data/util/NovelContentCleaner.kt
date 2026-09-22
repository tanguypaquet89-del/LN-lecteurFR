package com.nahrahviing.lecteurnovel.data.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/**
 * Moteur de nettoyage de contenu de roman.
 * Supprime proprement toutes les balises HTML (<p class="...">, <span>, <h2>...),
 * décode les entités HTML (&nbsp;, &amp;, etc.) et formate en paragraphes nets.
 */
object NovelContentCleaner {

    /**
     * Nettoie une chaîne de texte pouvant contenir du code HTML brut
     * pour produire un texte pur, parfaitement lisible et indenté en paragraphes.
     */
    fun cleanToPlainText(rawText: String, novelTitle: String? = null): String {
        val paragraphs = formatToParagraphs(rawText, novelTitle)
        return paragraphs.joinToString("\n\n")
    }

    /**
     * Découpe et nettoie le contenu en une liste de paragraphes textuels purs.
     */
    fun formatToParagraphs(rawText: String, novelTitle: String? = null): List<String> {
        if (rawText.isBlank()) return emptyList()

        var text = rawText.trim()

        // 1. Supprimer scripts et styles éventuels
        text = text.replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("(?is)<style.*?</style>"), "")

        // 2. Si le texte commence par un titre <h2> ou <h1> identique au titre du roman, le retirer
        val hMatch = Regex("""^\s*<h[1-6][^>]*>(.*?)</h[1-6]>""", RegexOption.IGNORE_CASE).find(text)
        if (hMatch != null) {
            val headerText = hMatch.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
            if (novelTitle != null && headerText.equals(novelTitle.trim(), ignoreCase = true)) {
                text = text.substring(hMatch.range.last + 1).trim()
            } else if (novelTitle == null || headerText.contains("Roman ", ignoreCase = true)) {
                text = text.substring(hMatch.range.last + 1).trim()
            }
        }

        // 3. Remplacer les balises de saut de ligne et fins de blocs par des retours à la ligne
        text = text
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(?:p|div|h[1-6]|li|tr|article|section)>"), "\n\n")
            .replace(Regex("(?i)<(?:p|div|h[1-6]|li|tr|article|section)[^>]*>"), "\n")

        // 4. Supprimer toutes les balises HTML restantes (<span...>, </span>, etc.)
        text = text.replace(Regex("<[^>]+>"), "")

        // 5. Décoder toutes les entités HTML (&nbsp;, &eacute;, &#39;, &quot;, &amp;, etc.)
        text = Parser.unescapeEntities(text, false)

        // Remplacer les espaces insécables résiduels
        text = text.replace('\u00A0', ' ')

        // 6. Découper par paragraphes et nettoyer
        val rawParagraphs = text.split(Regex("""\n{2,}"""))
        val result = mutableListOf<String>()

        for (p in rawParagraphs) {
            // Nettoyer les sauts de lignes internes uniques dans un paragraphe
            val trimmed = p.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            if (trimmed.isNotBlank()) {
                result.add(trimmed)
            }
        }

        // Si le premier paragraphe est exactement le titre du roman, le supprimer pour éviter les doublons
        if (result.isNotEmpty() && novelTitle != null) {
            val first = result.first().trim()
            if (first.equals(novelTitle.trim(), ignoreCase = true)) {
                result.removeAt(0)
            }
        }

        return result
    }

    /**
     * Extraction directe depuis le DOM Jsoup pour les parsers.
     */
    fun extractCleanStoryFromDoc(
        doc: Document,
        chapterUrl: String? = null,
        novelTitle: String? = null
    ): String {
        val container: Element = when {
            chapterUrl?.contains("novelfrance.fr") == true -> {
                doc.select(".chapter-content, #chapter-content, [data-paragraph-id]").firstOrNull()?.parent()
                    ?: doc.select(".chapter-content, #chapter-content").firstOrNull()
            }
            chapterUrl?.contains("purrfiction.io") == true -> {
                doc.select(".chapter-body, .reading-content, .story-text").firstOrNull()
            }
            chapterUrl?.contains("soreyawari.com") == true -> {
                doc.select(".entry-content").firstOrNull()
            }
            // Sélecteurs spécifiques pour Trad-Index
            chapterUrl?.contains("trad-index.com") == true -> {
                doc.select("main, article, #content, .chapter-content").firstOrNull()
            }
            chapterUrl?.contains("chireads.com") == true -> {
                doc.select(".reading-content, .text-left, .entry-content_wrap").firstOrNull()
            }
            else -> {
                doc.select(
                    ".reading-content, .chapter-content, #chapter-content, .chapter-body, " +
                    ".entry-content, .text-left, .story-text, article, #content"
                ).firstOrNull()
            }
        } ?: doc.body() ?: return ""

        val cloned = container.clone()
        cloned.select("script, style, iframe, form, nav, footer, .sharedaddy, .ads, .chapter-nav").remove()

        val textBlocks = cloned.select("p.narration, p.dialogue, p.game-badge, p, div.prose p, [data-paragraph-id]")
        if (textBlocks.isNotEmpty()) {
            val sb = StringBuilder()
            for (p in textBlocks) {
                val pText = p.text().trim()
                if (pText.isNotBlank()) {
                    sb.append(pText).append("\n\n")
                }
            }
            val raw = sb.toString()
            return cleanToPlainText(raw, novelTitle)
        }

        val html = cloned.html()
        return cleanToPlainText(html, novelTitle)
    }

    /**
     * Alias de nettoyage pour compatibilité.
     */
    fun cleanChapterContent(rawText: String, novelTitle: String? = null): String {
        return cleanToPlainText(rawText, novelTitle)
    }
}
