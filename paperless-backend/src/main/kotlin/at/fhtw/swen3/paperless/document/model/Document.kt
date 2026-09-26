package at.fhtw.swen3.paperless.document.model

import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import java.time.Instant
import java.util.UUID

data class Document(
    val id: UUID,
    val title: String,
    val originalFilename: String,
    val contentType: String,
    val fileSize: Long,
    val status: ProcessingStatus,
    val ocrContent: String?,
    val summary: String?,
    val storageKey: String?,
    val documentType: DocumentType?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val ownerId: UUID,
    val teamId: UUID? = null
)
