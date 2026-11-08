package at.fhtw.swen3.paperless.ocr.model

import java.time.Instant

data class IndexedDocument(
    val id: String,
    val content: String,
    val indexedAt: Instant
)
