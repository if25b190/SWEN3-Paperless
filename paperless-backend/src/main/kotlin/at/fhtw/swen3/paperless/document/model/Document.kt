package at.fhtw.swen3.paperless.document.model

import at.fhtw.swen3.paperless.correspondent.model.Correspondent
import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import java.time.Instant

data class Document(
    val id: Long,
    val title: String,
    val originalFilename: String,
    val contentType: String,
    val fileSize: Long,
    val status: ProcessingStatus,
    val ocrContent: String?,
    val summary: String?,
    val storageKey: String?,
    val correspondent: Correspondent?,
    val documentType: DocumentType?,
    val createdAt: Instant,
    val updatedAt: Instant
)
