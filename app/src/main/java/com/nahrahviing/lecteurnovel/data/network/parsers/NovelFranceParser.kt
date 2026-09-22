package com.nahrahviing.lecteurnovel.data.network.parsers

import com.nahrahviing.lecteurnovel.data.model.Chapter
import com.nahrahviing.lecteurnovel.data.model.NovelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup

class NovelFranceParser : SiteParser {
    override val name = "NovelFrance"
    override val baseUrl = "https://novelfrance.fr"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36"

    override fun canParse(url: String): Boolean {
        return url.lowercase().contains("novelfrance.fr")
    }

    override fun isNovelUrl(url: String): Boolean {
        val clean = url.lowercase()
        return clean.contains("novelfrance.fr") && clean.contains("/novel/") && !clean.contains("/chapter-")
    }

    override suspend fun extractNovel(url: String, html: String?): NovelInfo? = withContext(Dispatchers.IO) {
        try {
            val slugMatch = Regex("/novel/([^/?#]+)").find(url)
            val slug = slugMatch?.groupValues?.get(1) ?: return@withContext null

            // 1. Get metadata from API
            var title = slug.replace("-", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            var author = "Auteur inconnu"
            var coverUrl = ""
            var synopsis = ""
            val genres = mutableListOf<String>()

            try {
                val apiUrl = "https://novelfrance.fr/api/novels/$slug"
                val res = Jsoup.connect(apiUrl)
                    .userAgent(userAgent)
                    .ignoreContentType(true)
                    .timeout(10000)
                    .execute()
                val json = JSONObject(res.body())
                title = json.optString("title", title)
                author = json.optString("author", author)
                coverUrl = json.optString("coverImage", "")
                if (coverUrl.isNotEmpty() && !coverUrl.startsWith("http")) {
                    coverUrl = "https://novelfrance.fr$coverUrl"
                }
                synopsis = json.optString("description", "")
                val tagsArr = json.optJSONArray("genres")
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        genres.add(tagsArr.getJSONObject(i).optString("name"))
                    }
                }
            } catch (_: Exception) {
            }

            // 2. Fetch first batch of chapters to check total
            val take = 100
            val firstBatchUrl = "https://novelfrance.fr/api/chapters/$slug?skip=0&take=$take&order=asc"
            val firstRes = Jsoup.connect(firstBatchUrl)
                .userAgent(userAgent)
                .ignoreContentType(true)
                .timeout(15000)
                .execute()
            val firstJson = JSONObject(firstRes.body())
            val totalChapters = firstJson.optInt("total", 0)
            val firstList = firstJson.optJSONArray("chapters")

            val chapters = mutableListOf<Chapter>()

            if (firstList != null && firstList.length() > 0) {
                for (i in 0 until firstList.length()) {
                    val c = firstList.getJSONObject(i)
                    val num = c.optInt("chapterNumber", 0)
                    val cTitle = c.optString("title", "").trim()
                    val cSlug = c.optString("slug", "")
                    val name = if (cTitle.isNotEmpty()) "Chapitre $num - $cTitle" else "Chapitre $num"
                    val chapUrl = "https://novelfrance.fr/novel/$slug/$cSlug"
                    chapters.add(Chapter(index = num, title = name, url = chapUrl))
                }

                // If more chapters exist, fetch remaining batches concurrently
                val numBatches = if (totalChapters > take) {
                    ((totalChapters - 1) / take)
                } else if (firstJson.optBoolean("hasMore", false)) {
                    20
                } else {
                    0
                }

                if (numBatches > 0) {
                    // Fetch in chunks of 5 parallel requests to avoid overwhelming the server
                    val batchIndices = (1..numBatches).toList()
                    val chunkSize = 5
                    for (chunk in batchIndices.chunked(chunkSize)) {
                        val batchResults = chunk.map { batchIdx ->
                            async {
                                try {
                                    val skip = batchIdx * take
                                    val urlBatch = "https://novelfrance.fr/api/chapters/$slug?skip=$skip&take=$take&order=asc"
                                    val bRes = Jsoup.connect(urlBatch)
                                        .userAgent(userAgent)
                                        .ignoreContentType(true)
                                        .timeout(15000)
                                        .execute()
                                    val bJson = JSONObject(bRes.body())
                                    val bArr = bJson.optJSONArray("chapters")
                                    val chunkList = mutableListOf<Chapter>()
                                    if (bArr != null) {
                                        for (j in 0 until bArr.length()) {
                                            val c = bArr.getJSONObject(j)
                                            val num = c.optInt("chapterNumber", 0)
                                            val cTitle = c.optString("title", "").trim()
                                            val cSlug = c.optString("slug", "")
                                            val name = if (cTitle.isNotEmpty()) "Chapitre $num - $cTitle" else "Chapitre $num"
                                            val chapUrl = "https://novelfrance.fr/novel/$slug/$cSlug"
                                            chunkList.add(Chapter(index = num, title = name, url = chapUrl))
                                        }
                                    }
                                    chunkList
                                } catch (e: Exception) {
                                    emptyList<Chapter>()
                                }
                            }
                        }.awaitAll()

                        batchResults.forEach { chapters.addAll(it) }
                    }
                }
            }

            // Sort by chapter index to be 100% strictly ordered
            val sortedChapters = chapters
                .distinctBy { it.url }
                .sortedBy { it.index }
                .mapIndexed { idx, chap -> chap.copy(index = idx + 1) }

            if (sortedChapters.isNotEmpty()) {
                NovelInfo(
                    title = title,
                    author = author,
                    coverUrl = coverUrl,
                    synopsis = synopsis,
                    genres = genres,
                    chapters = sortedChapters,
                    originalUrl = url
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun fetchChapterContent(url: String, novelTitle: String): String = withContext(Dispatchers.IO) {
        try {
            // Extract novelSlug and chapterSlug from URL
            // e.g. https://novelfrance.fr/novel/shadow-slave/chapter-1
            // or https://novelfrance.fr/api/chapters/shadow-slave/chapter-1
            val pathSegments = url.trimEnd('/').split("/")
            if (pathSegments.size >= 2) {
                val chapterSlug = pathSegments.last()
                val novelSlug = pathSegments[pathSegments.size - 2]

                val apiUrl = "https://novelfrance.fr/api/chapters/$novelSlug/$chapterSlug"
                try {
                    val res = Jsoup.connect(apiUrl)
                        .userAgent(userAgent)
                        .ignoreContentType(true)
                        .timeout(15000)
                        .execute()
                    val data = JSONObject(res.body())
                    val title = data.optString("title", "").trim()
                    val paragraphs = data.optJSONArray("paragraphs")

                    val sb = StringBuilder()

                    if (paragraphs != null && paragraphs.length() > 0) {
                        for (i in 0 until paragraphs.length()) {
                            val p = paragraphs.getJSONObject(i)
                            val content = p.optString("content", "").trim()
                            if (content.isBlank()) continue
                            // Skip if index 0 is exact title repetition
                            if (title.isNotEmpty() && p.optInt("index", -1) == 0 && content.equals(title, ignoreCase = true)) {
                                continue
                            }
                            sb.append(content).append("\n\n")
                        }
                        return@withContext com.nahrahviing.lecteurnovel.data.util.NovelContentCleaner.cleanToPlainText(sb.toString(), novelTitle)
                    }
                } catch (_: Exception) {
                }
            }

            // Fallback: standard HTML parsing if API failed
            val doc = Jsoup.connect(url).userAgent(userAgent).timeout(15000).get()
            val textBlocks = doc.select("div.prose p, article p, .chapter-content p")
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

