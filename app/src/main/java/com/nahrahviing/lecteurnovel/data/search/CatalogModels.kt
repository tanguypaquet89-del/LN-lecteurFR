package com.nahrahviing.lecteurnovel.data.search

data class NovelGenreFilter(
    val id: String,
    val name: String,
    val tagUrl: String
)

/**
 * Plateformes officiellement supportées et accessibles via les raccourcis du navigateur intégré.
 * Chaque entrée fournit l'identifiant, le nom affiché, le drapeau représentatif, l'URL d'accueil et la description.
 */
object SupportedPlatforms {
    data class Platform(
        val id: String,
        val name: String,
        val flag: String,
        val homeUrl: String,
        val description: String
    )

    val list = listOf(
        Platform("novelfrance", "NovelFrance", "🇫🇷", "https://novelfrance.fr", "Webnovels et Light Novels en français"),
        Platform("chireads", "ChiReads", "🇨🇳", "https://chireads.com", "Romans chinois et asiatiques traduits"),
        Platform("purrfiction", "Purrfiction", "🐱", "https://purrfiction.io", "Plateforme gratuite de web novels originaux et traduits"),
        Platform("soreyawari", "Soreyawari", "🌸", "https://soreyawari.com", "Light novels japonais et isekai en français"),
        Platform("royalroad", "Royal Road", "👑", "https://www.royalroad.com", "Webnovels et LitRPG originaux"),
        Platform("tradindex", "Trad-Index", "🔍", "https://trad-index.com", "Annuaire et indexeur de webnovels francophones")
    )
}

object ChiReadsCatalog {
    val genres = listOf(
        NovelGenreFilter("all", "Tous", "https://chireads.com"),
        NovelGenreFilter("action", "Action", "https://chireads.com/genre/action/"),
        NovelGenreFilter("fantasy", "Fantasy", "https://chireads.com/genre/fantasy/"),
        NovelGenreFilter("xianxia", "Xianxia / Wuxia", "https://chireads.com/genre/xianxia/"),
        NovelGenreFilter("adventure", "Aventure", "https://chireads.com/genre/adventure/"),
        NovelGenreFilter("romance", "Romance", "https://chireads.com/genre/romance/"),
        NovelGenreFilter("sci-fi", "Sci-Fi", "https://chireads.com/genre/sci-fi/"),
        NovelGenreFilter("mystery", "Mystère", "https://chireads.com/genre/mystery/")
    )
}

object NovelFranceCatalog {
    const val HOME_URL = "https://novelfrance.fr"
    const val BROWSE_URL = "https://novelfrance.fr/browse"

    val genres = listOf(
        NovelGenreFilter("all", "Tous", "https://novelfrance.fr/browse"),
        NovelGenreFilter("aventure", "Aventure", "https://novelfrance.fr/browse?genre=aventure"),
        NovelGenreFilter("fantaisie", "Fantaisie", "https://novelfrance.fr/browse?genre=fantaisie"),
        NovelGenreFilter("litrpg", "LITRPG", "https://novelfrance.fr/browse?genre=litrpg"),
        NovelGenreFilter("romance", "Romance", "https://novelfrance.fr/browse?genre=romance"),
        NovelGenreFilter("action", "Action", "https://novelfrance.fr/browse?genre=action"),
        NovelGenreFilter("tragedie", "Tragédie", "https://novelfrance.fr/browse?genre=trag-die"),
        NovelGenreFilter("sci-fi", "Sci-Fi", "https://novelfrance.fr/browse?genre=sci-fi")
    )
}
