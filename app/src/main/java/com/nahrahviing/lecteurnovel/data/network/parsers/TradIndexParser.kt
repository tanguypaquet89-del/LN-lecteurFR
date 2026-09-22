package com.nahrahviing.lecteurnovel.data.network.parsers

import com.nahrahviing.lecteurnovel.data.model.Chapter
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Parseur dédié exclusivement au site Trad-Index (https://trad-index.com).
 * 
 * Trad-Index est un annuaire et portail de webnovels francophones.
 * Ce parseur prend en charge :
 * - La détection des fiches d'œuvres (/oeuvre/, /roman/, /novel/)
 * - L'extraction des métadonnées (titre, auteur, couverture Cloudinary, synopsis, genres)
 * - La pagination asynchrone des chapitres avec sémaphore de limitation de concurrence
 * - Le tri ordonné des chapitres par numéro croissant
 * - La récupération et le nettoyage du texte des chapitres (y compris via le payload Next.js __NEXT_DATA__)
 */
class TradIndexParser : SiteParser {
    override val name = "Trad-Index"
    override val baseUrl = "https://trad-index.com"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val httpTimeoutMs = 15000

    /**
     * Vérifie si l'URL fournie correspond au domaine Trad-Index.
     */
    override fun canParse(url: String): Boolean {
        return url.lowercase().contains("trad-index.com")
    }

    /**
     * Vérifie si l'URL pointe vers une fiche de roman (et non un chapitre individuel ou une page générale).
     */
    override fun isNovelUrl(url: String): Boolean {
        val clean = url.lowercase()
        val isMatch = clean.contains("trad-index.com")
        return isMatch && 
            (clean.contains("/oeuvre/") || clean.contains("/roman/") || clean.contains("/novel/")) && 
            !clean.contains("/chapitre/") && 
            !clean.contains("/lire/")
    }

    /**
     * Structure interne pour stocker et trier les chapitres extraits avant conversion finale.
     * @param num Numéro du chapitre (permet le tri numérique fiable, ex: 1.5, 2, 10)
     * @param title Titre lisible du chapitre
     * @param url URL complète du chapitre pour la lecture
     */
    private data class ParsedTradChapter(val num: Double, val title: String, val url: String)

    /**
     * Point d'entrée principal pour extraire les métadonnées et la liste complète des chapitres.
     */
    override suspend fun extractNovel(url: String, html: String?): NovelInfo? = withContext(Dispatchers.IO) {
        extractTradIndex(url, html)
    }

    /**
     * Extraction détaillée depuis Trad-Index avec pagination dynamique.
     */
    private suspend fun extractTradIndex(url: String, html: String?): NovelInfo? = withContext(Dispatchers.IO) {
        try {
            val site = baseUrl
            // Extraction du slug de l'œuvre depuis l'URL (ex: /oeuvre/mon-super-roman)
            val slugMatch = Regex("""/(?:oeuvre|roman|novel)/([^/?#]+)""").find(url)
            val slug = slugMatch?.groupValues?.get(1) ?: ""
            val cleanNovelUrl = if (slug.isNotEmpty()) "$site/oeuvre/$slug" else url.substringBefore('?')

            // Récupération du document HTML initial (soit fourni par la WebView, soit téléchargé via Jsoup)
            val doc = if (html != null) {
                Jsoup.parse(html, cleanNovelUrl)
            } else {
                Jsoup.connect(cleanNovelUrl)
                    .userAgent(userAgent)
                    .timeout(httpTimeoutMs)
                    .get()
            }
            
            // 1. Extraction des métadonnées du roman
            val defaultTitle = "Roman Trad-Index"
            val title = doc.select("h1").firstOrNull()?.text()?.trim() ?: defaultTitle
            val author = doc.select(".author, a[href*='/auteur/']").firstOrNull()?.text()?.trim() ?: "Auteur inconnu"
            val coverUrl = doc.select("img[src*='cloudinary.com'], img[alt*='Couverture']").firstOrNull()?.absUrl("src") ?: ""
            val synopsis = doc.select("p.text-sm.text-warm-gray, .synopsis").firstOrNull()?.text()?.trim() ?: ""
            val genres = doc.select("a[href*='/tags-genres/']").map { it.text().trim() }.filter { it.isNotBlank() }
            
            // 2. Fonction locale d'extraction des liens de chapitres depuis un document HTML
            fun extractChaptersFromDoc(d: Document): List<ParsedTradChapter> {
                val list = mutableListOf<ParsedTradChapter>()
                val links = d.select("a[href*='/chapitre/'], a[href*='/lire/']")
                for (el in links) {
                    var chapUrl = el.absUrl("href")
                    if (chapUrl.isEmpty()) {
                        val rel = el.attr("href")
                        chapUrl = if (rel.startsWith("/")) "$site$rel" else "$site/$rel"
                    }
                    val text = el.text().trim()
                    val textLower = text.lowercase()
                    // Ignorer les boutons de navigation rapide génériques ("Commencer la lecture", "Dernier chapitre")
                    if (textLower.contains("commencer") || textLower.contains("dernier")) {
                        continue
                    }
                    // Vérifier que le chapitre appartient bien au même roman
                    if (slug.isNotEmpty() && !chapUrl.contains("/$slug/chapitre/")) {
                        continue
                    }
                    val numMatch = Regex("""/chapitre/(\d+(?:[.,]\d+)?)""").find(chapUrl) ?: continue
                    val chapNumStr = numMatch.groupValues[1]
                    val chapNum = chapNumStr.replace(',', '.').toDoubleOrNull() ?: 0.0

                    // Récupération d'un titre soigné
                    val truncateSpan = el.select(".truncate").firstOrNull()?.text()?.trim()
                    val displayTitle = if (!truncateSpan.isNullOrBlank()) {
                        truncateSpan
                    } else {
                        val clean = text.replace(Regex("""^\d+\s*"""), "")
                            .replace(Regex("""\s+\d{1,2}\s+\p{L}+\s+\d{4}$"""), "")
                            .trim()
                        if (clean.isNotBlank() && !Regex("""^\d+$""").matches(clean)) {
                            clean
                        } else {
                            "Chapitre $chapNumStr"
                        }
                    }
                    list.add(ParsedTradChapter(chapNum, displayTitle, chapUrl))
                }
                return list
            }

            // 3. Détection du nombre maximal de pages de chapitres dans la pagination
            var currentMaxPage = 1
            doc.select("a[href*='page=']").forEach { el ->
                val p = Regex("""page=(\d+)""").find(el.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                if (p != null && p > currentMaxPage) {
                    currentMaxPage = p
                }
            }
            Regex("""(?:[?&]|&amp;|\\u0026)page=(\d+)""").findAll(doc.html()).forEach { m ->
                val p = m.groupValues[1].toIntOrNull()
                if (p != null && p > currentMaxPage) {
                    currentMaxPage = p
                }
            }

            val allChaptersMap = ConcurrentHashMap<String, ParsedTradChapter>()
            extractChaptersFromDoc(doc).forEach { chap ->
                allChaptersMap[chap.url] = chap
            }

            // 4. Si pagination présente, récupérer les pages suivantes en parallèle (limité à 10 requêtes simultanées)
            if (currentMaxPage > 1 && slug.isNotEmpty()) {
                val fetchedPages = Collections.synchronizedSet(mutableSetOf(1))
                val semaphore = Semaphore(10)
                while (true) {
                    val pagesToFetch = (2..currentMaxPage).filter { !fetchedPages.contains(it) }
                    if (pagesToFetch.isEmpty()) break

                    coroutineScope {
                        pagesToFetch.map { pageNum ->
                            async(Dispatchers.IO) {
                                fetchedPages.add(pageNum)
                                semaphore.withPermit {
                                    val pageUrl = "$cleanNovelUrl?onglet=chapitres&tri=desc&page=$pageNum"
                                    runCatching {
                                        val pageDoc = Jsoup.connect(pageUrl)
                                            .userAgent(userAgent)
                                            .timeout(httpTimeoutMs)
                                            .get()

                                        // Ajuster la borne max si de nouvelles pages sont découvertes
                                        Regex("""(?:[?&]|&amp;|\\u0026)page=(\d+)""").findAll(pageDoc.html()).forEach { m ->
                                            val p = m.groupValues[1].toIntOrNull()
                                            if (p != null && p > currentMaxPage) {
                                                currentMaxPage = p
                                            }
                                        }

                                        extractChaptersFromDoc(pageDoc).forEach { chap ->
                                            allChaptersMap[chap.url] = chap
                                        }
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                }
            }

            // 5. Tri strict des chapitres par numéro chronologique croissant
            val sortedChapters = allChaptersMap.values.sortedWith(
                compareBy<ParsedTradChapter> { it.num }
                    .thenBy { it.title }
            )

            // Indexation finale séquentielle (1..N)
            val finalChapters = sortedChapters.mapIndexed { idx, chap ->
                Chapter(index = idx + 1, title = chap.title, url = chap.url)
            }

            if (finalChapters.isNotEmpty()) {
                return@withContext NovelInfo(title, author, coverUrl, synopsis, genres, finalChapters, cleanNovelUrl)
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Récupère le contenu textuel d'un chapitre Trad-Index et applique les filtres de nettoyage.
     */
    override suspend fun fetchChapterContent(url: String, novelTitle: String): String = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(httpTimeoutMs)
                .get()

            // 1. Recherche des conteneurs de texte de lecture principaux
            val textBlocks = doc.select("p.narration, p.dialogue, p.game-badge, div.prose p, div.chapter-content p, article p, .reading-content p")
            val contentStr = StringBuilder()
            
            if (textBlocks.isNotEmpty()) {
                for (p in textBlocks) {
                    val pText = p.text().trim()
                    if (pText.isNotBlank()) {
                        contentStr.append(pText).append("\n\n")
                    }
                }
            } else {
                // 2. Si le DOM est rendu dynamiquement via Next.js, analyser le JSON __NEXT_DATA__
                val scripts = doc.select("script#__NEXT_DATA__")
                if (scripts.isNotEmpty()) {
                    val json = JSONObject(scripts.first()!!.data())
                    val content = json.optJSONObject("props")
                        ?.optJSONObject("pageProps")
                        ?.optJSONObject("chapter")
                        ?.optString("content", "")
                    if (!content.isNullOrBlank()) {
                        return@withContext NovelContentCleaner.cleanToPlainText(content, novelTitle)
                    }
                }
            }
            
            // 3. Nettoyage final des balises et artefacts
            NovelContentCleaner.cleanToPlainText(contentStr.toString(), novelTitle)
        } catch (e: Exception) {
            "Erreur lors de la récupération : ${e.message}"
        }
    }
}
