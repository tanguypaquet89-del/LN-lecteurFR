package com.nahrahviing.lecteurnovel.data.model

data class Chapter(
    val index: Int,
    val title: String,
    val url: String,
    var content: String = ""
)

data class NovelInfo(
    val title: String,
    val author: String,
    val coverUrl: String,
    val synopsis: String,
    val genres: List<String>,
    val chapters: List<Chapter>,
    val originalUrl: String,
    val status: String = "En cours"
)
