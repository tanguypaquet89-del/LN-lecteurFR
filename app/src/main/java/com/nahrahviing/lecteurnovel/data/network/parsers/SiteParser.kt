package com.nahrahviing.lecteurnovel.data.network.parsers

import com.nahrahviing.lecteurnovel.data.model.NovelInfo

interface SiteParser {
    val name: String
    val baseUrl: String
    
    fun canParse(url: String): Boolean
    
    fun isNovelUrl(url: String): Boolean = canParse(url)
    
    /**
     * Extracts the novel information. If HTML is provided (e.g. from WebView), it uses it, 
     * otherwise it fetches via Jsoup/API.
     */
    suspend fun extractNovel(url: String, html: String? = null): NovelInfo?
    
    /**
     * Fetches the content of a specific chapter.
     */
    suspend fun fetchChapterContent(url: String, novelTitle: String): String
}
