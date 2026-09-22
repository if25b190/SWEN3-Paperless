package at.fhtw.swen3.paperless.search.model

import at.fhtw.swen3.paperless.document.model.Document

data class SearchResult(
    val document: Document,
    val score: Float,
    val highlights: List<String> = emptyList()
)
