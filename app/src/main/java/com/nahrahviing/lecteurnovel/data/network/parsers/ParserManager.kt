package com.nahrahviing.lecteurnovel.data.network.parsers

import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import com.nahrahviing.lecteurnovel.data.network.parsers.dynamic.DynamicParserManager

/**
 * Orchestrateur central des parseurs de sites webnovels.
 * 
 * Architecture hybride :
 * 1. Parseurs dynamiques (DynamicNovelParser) : configurés via des fichiers JSON (dans les assets ou ajoutés par l'utilisateur).
 *    Permet d'ajouter ou mettre à jour des sources sans recompiler l'application.
 * 2. Parseurs statiques (SiteParser) : implémentations Kotlin natives pour les plateformes nécessitant
 *    une logique complexe d'API, d'authentification ou de pagination (NovelFrance, Purrfiction, Trad-Index, ChiReads, Soreyawari).
 */
object ParserManager {
    /**
     * Liste ordonnée des parseurs statiques natifs.
     */
    private val staticParsers = listOf(
        NovelFranceParser(),
        PurrfictionParser(),
        TradIndexParser(),
        ChiReadsParser(),
        SoreyawariParser()
    )

    /**
     * Détermine si l'URL courante correspond à une fiche descriptive de roman
     * (pour afficher le bouton flottant "Télécharger / Lire le roman").
     */
    fun isNovelPage(url: String): Boolean {
        // Vérifier d'abord les parseurs dynamiques JSON
        val dynamic = DynamicParserManager.getParsers()
        if (dynamic.any { it.isNovelUrl(url) }) return true
        return staticParsers.any { it.isNovelUrl(url) }
    }

    /**
     * Extrait les métadonnées complètes et la liste des chapitres d'un roman.
     * Priorise le parseur dynamique associé à l'URL, avec repli transparent sur le parseur statique.
     */
    suspend fun extractNovel(url: String, html: String? = null): NovelInfo? {
        val dynamicParser = DynamicParserManager.getParserForUrl(url)
        if (dynamicParser != null) {
            val res = dynamicParser.extractNovel(url, html)
            if (res != null) return res
        }

        val staticParser = staticParsers.firstOrNull { it.isNovelUrl(url) } ?: staticParsers.firstOrNull { it.canParse(url) }
        return staticParser?.extractNovel(url, html)
    }

    /**
     * Télécharge et nettoie le texte d'un chapitre individuel.
     */
    suspend fun fetchChapterContent(url: String, novelTitle: String): String {
        val dynamicParser = DynamicParserManager.getParsers().firstOrNull { it.canParse(url) }
        if (dynamicParser != null) {
            val content = dynamicParser.fetchChapterContent(url, novelTitle)
            if (!content.startsWith("Erreur")) return content
        }

        val staticParser = staticParsers.firstOrNull { it.canParse(url) }
        return staticParser?.fetchChapterContent(url, novelTitle) ?: "Erreur: Aucun parseur trouvé pour cette URL."
    }
}
