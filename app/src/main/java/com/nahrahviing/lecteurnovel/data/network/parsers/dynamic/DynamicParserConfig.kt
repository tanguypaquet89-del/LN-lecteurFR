package com.nahrahviing.lecteurnovel.data.network.parsers.dynamic

import org.json.JSONArray
import org.json.JSONObject

data class DynamicParserConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val version: Int = 1,
    val flag: String = "📚",
    val description: String = "",
    val isCustom: Boolean = false,
    val urlPatterns: List<String> = emptyList(),
    val novelUrlPatterns: List<String> = emptyList(),
    val novelUrlExcludePatterns: List<String> = emptyList(),
    val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36",
    val headers: Map<String, String> = emptyMap(),

    // Novel Metadata Selectors
    val titleSelector: String = "h1",
    val authorSelector: String? = null,
    val authorDefault: String = "Auteur inconnu",
    val coverSelector: String? = null,
    val coverAttr: String = "src",
    val coverFallbackAttr: String = "data-src",
    val synopsisSelector: String? = null,
    val genresSelector: String? = null,

    // Chapter List Selectors
    val chapterListSelector: String = "a[href*='chapter']",
    val chapterTitleSelector: String? = null,
    val chapterTitleAttr: String = "title",
    val chapterUrlAttr: String = "href",
    val chaptersReverse: Boolean = false,

    // Chapter Content Selectors
    val contentSelector: String = "p",
    val contentRemoveSelectors: List<String> = listOf("script", "style", ".ads", "iframe"),
    val contentCleanTitle: Boolean = true
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("baseUrl", baseUrl)
        json.put("version", version)
        json.put("flag", flag)
        json.put("description", description)
        json.put("isCustom", isCustom)

        json.put("urlPatterns", JSONArray(urlPatterns))
        json.put("novelUrlPatterns", JSONArray(novelUrlPatterns))
        json.put("novelUrlExcludePatterns", JSONArray(novelUrlExcludePatterns))
        json.put("userAgent", userAgent)

        val headersJson = JSONObject()
        headers.forEach { (k, v) -> headersJson.put(k, v) }
        json.put("headers", headersJson)

        val selectors = JSONObject()
        selectors.put("title", titleSelector)
        if (authorSelector != null) selectors.put("author", authorSelector)
        selectors.put("authorDefault", authorDefault)
        if (coverSelector != null) selectors.put("cover", coverSelector)
        selectors.put("coverAttr", coverAttr)
        selectors.put("coverFallbackAttr", coverFallbackAttr)
        if (synopsisSelector != null) selectors.put("synopsis", synopsisSelector)
        if (genresSelector != null) selectors.put("genres", genresSelector)

        val chaptersJson = JSONObject()
        chaptersJson.put("list", chapterListSelector)
        if (chapterTitleSelector != null) chaptersJson.put("titleSelector", chapterTitleSelector)
        chaptersJson.put("titleAttr", chapterTitleAttr)
        chaptersJson.put("urlAttr", chapterUrlAttr)
        chaptersJson.put("reverse", chaptersReverse)
        selectors.put("chapters", chaptersJson)

        val contentJson = JSONObject()
        contentJson.put("body", contentSelector)
        contentJson.put("remove", JSONArray(contentRemoveSelectors))
        contentJson.put("cleanTitle", contentCleanTitle)
        selectors.put("content", contentJson)

        json.put("selectors", selectors)
        return json.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String, isCustom: Boolean = false): DynamicParserConfig {
            val json = JSONObject(jsonStr)
            val id = json.optString("id", "").ifEmpty { "parser_" + System.currentTimeMillis() }
            val name = json.optString("name", "Nouveau parseur")
            val baseUrl = json.optString("baseUrl", "https://")
            val version = json.optInt("version", 1)
            val flag = json.optString("flag", "📚")
            val description = json.optString("description", "")
            val custom = json.optBoolean("isCustom", isCustom)

            val urlPatterns = mutableListOf<String>()
            json.optJSONArray("urlPatterns")?.let { arr ->
                for (i in 0 until arr.length()) urlPatterns.add(arr.getString(i))
            }

            val novelUrlPatterns = mutableListOf<String>()
            json.optJSONArray("novelUrlPatterns")?.let { arr ->
                for (i in 0 until arr.length()) novelUrlPatterns.add(arr.getString(i))
            }

            val novelUrlExcludePatterns = mutableListOf<String>()
            json.optJSONArray("novelUrlExcludePatterns")?.let { arr ->
                for (i in 0 until arr.length()) novelUrlExcludePatterns.add(arr.getString(i))
            }

            val userAgent = json.optString(
                "userAgent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36"
            )

            val headers = mutableMapOf<String, String>()
            json.optJSONObject("headers")?.let { obj ->
                obj.keys().forEach { k -> headers[k] = obj.getString(k) }
            }

            val selectors = json.optJSONObject("selectors") ?: JSONObject()
            val titleSelector = selectors.optString("title", "h1")
            val authorSelector = if (selectors.has("author")) selectors.optString("author") else null
            val authorDefault = selectors.optString("authorDefault", "Auteur inconnu")
            val coverSelector = if (selectors.has("cover")) selectors.optString("cover") else null
            val coverAttr = selectors.optString("coverAttr", "src")
            val coverFallbackAttr = selectors.optString("coverFallbackAttr", "data-src")
            val synopsisSelector = if (selectors.has("synopsis")) selectors.optString("synopsis") else null
            val genresSelector = if (selectors.has("genres")) selectors.optString("genres") else null

            val chaptersJson = selectors.optJSONObject("chapters") ?: JSONObject()
            val chapterListSelector = chaptersJson.optString("list", "a[href*='chapter']")
            val chapterTitleSelector = if (chaptersJson.has("titleSelector")) chaptersJson.optString("titleSelector") else null
            val chapterTitleAttr = chaptersJson.optString("titleAttr", "title")
            val chapterUrlAttr = chaptersJson.optString("urlAttr", "href")
            val chaptersReverse = chaptersJson.optBoolean("reverse", false)

            val contentJson = selectors.optJSONObject("content") ?: JSONObject()
            val contentSelector = contentJson.optString("body", "p")
            val contentRemoveSelectors = mutableListOf<String>()
            contentJson.optJSONArray("remove")?.let { arr ->
                for (i in 0 until arr.length()) contentRemoveSelectors.add(arr.getString(i))
            }
            if (contentRemoveSelectors.isEmpty()) {
                contentRemoveSelectors.addAll(listOf("script", "style", ".ads", "iframe"))
            }
            val contentCleanTitle = contentJson.optBoolean("cleanTitle", true)

            return DynamicParserConfig(
                id = id,
                name = name,
                baseUrl = baseUrl,
                version = version,
                flag = flag,
                description = description,
                isCustom = custom,
                urlPatterns = urlPatterns,
                novelUrlPatterns = novelUrlPatterns,
                novelUrlExcludePatterns = novelUrlExcludePatterns,
                userAgent = userAgent,
                headers = headers,
                titleSelector = titleSelector,
                authorSelector = authorSelector,
                authorDefault = authorDefault,
                coverSelector = coverSelector,
                coverAttr = coverAttr,
                coverFallbackAttr = coverFallbackAttr,
                synopsisSelector = synopsisSelector,
                genresSelector = genresSelector,
                chapterListSelector = chapterListSelector,
                chapterTitleSelector = chapterTitleSelector,
                chapterTitleAttr = chapterTitleAttr,
                chapterUrlAttr = chapterUrlAttr,
                chaptersReverse = chaptersReverse,
                contentSelector = contentSelector,
                contentRemoveSelectors = contentRemoveSelectors,
                contentCleanTitle = contentCleanTitle
            )
        }
    }
}
