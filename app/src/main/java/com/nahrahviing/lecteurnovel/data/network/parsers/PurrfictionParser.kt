package com.nahrahviing.lecteurnovel.data.network.parsers

import com.nahrahviing.lecteurnovel.data.model.Chapter
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.jsoup.Jsoup

class PurrfictionParser : SiteParser {
    override val name = "Purrfiction"
    override val baseUrl = "https://purrfiction.io"

    override fun canParse(url: String): Boolean {
        return url.lowercase().contains("purrfiction.io")
    }

    override fun isNovelUrl(url: String): Boolean {
        val clean = url.lowercase()
        return clean.contains("purrfiction.io") && clean.contains("/book/")
    }

    override suspend fun extractNovel(url: String, html: String?): NovelInfo? = withContext(Dispatchers.IO) {
        try {
            val bookIdMatch = Regex("book/(\\d+)/").find(url)
            val bookId = bookIdMatch?.groupValues?.get(1)
            
            if (bookId != null) {
                val doc = if (html != null) Jsoup.parse(html, url) else Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(15000).get()
                val title = doc.select("h1, .story-title, .book-title").firstOrNull()?.text()?.trim() ?: "Roman Purrfiction"
                val author = doc.select(".author-name, .story-author").firstOrNull()?.text()?.trim() ?: "Auteur Purrfiction"
                val coverUrl = doc.select(".story-cover img, .book-cover img").firstOrNull()?.absUrl("src") ?: ""
                val synopsis = doc.select(".story-description, .book-synopsis, #synopsis").firstOrNull()?.text()?.trim() ?: ""
                val genres = doc.select(".tag, .genre, .badge").map { it.text().trim() }
                
                val chapters = mutableListOf<Chapter>()
                val apiUrl = "https://fr.purrfiction.io/V5/chapters?bookId=$bookId&language=FR"
                val jsonStr = Jsoup.connect(apiUrl)
                    .userAgent("Mozilla/5.0")
                    .ignoreContentType(true)
                    .timeout(15000)
                    .execute()
                    .body()
                val jsonArr = JSONArray(jsonStr)
                
                for (i in 0 until jsonArr.length()) {
                    val chapObj = jsonArr.getJSONObject(i)
                    val id = chapObj.optString("id", "")
                    val num = chapObj.optInt("number", i + 1)
                    val cTitle = chapObj.optString("title", "Chapitre $num")
                    val fullTitle = "Chapitre $num - $cTitle"
                    val cUrl = "https://purrfiction.io/story/$id/FR"
                    chapters.add(Chapter(index = num, title = fullTitle, url = cUrl))
                }
                
                if (chapters.isNotEmpty()) {
                    return@withContext NovelInfo(title, author, coverUrl, synopsis, genres, chapters, url)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun fetchChapterContent(url: String, novelTitle: String): String = withContext(Dispatchers.IO) {
        try {
            val chapterIdMatch = Regex("story/([a-zA-Z0-9-]+)/").find(url)
            val chapterId = chapterIdMatch?.groupValues?.get(1)
            if (chapterId != null) {
                val apiUrl = "https://fr.purrfiction.io/V5/chapters/$chapterId?language=FR"
                val jsonStr = Jsoup.connect(apiUrl)
                    .userAgent("Mozilla/5.0")
                    .ignoreContentType(true)
                    .timeout(15000)
                    .execute()
                    .body()
                val jsonObj = org.json.JSONObject(jsonStr)
                val contentArray = jsonObj.optJSONArray("content")
                if (contentArray != null) {
                    val contentStr = StringBuilder()
                    for (i in 0 until contentArray.length()) {
                        contentStr.append(contentArray.getString(i)).append("\n\n")
                    }
                    return@withContext com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner.cleanToPlainText(contentStr.toString(), novelTitle)
                }
            }
            // Fallback
            val doc = Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(15000).get()
            val textBlocks = doc.select(".reading-content p, .chapter-content p")
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
