package com.racha.ai.data.search

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val fullText: String? = null
)
