package at.fhtw.swen3.paperless.document.model

import java.util.UUID

data class DocumentUpload(
    val title: String,
    val originalFilename: String,
    val contentType: String,
    val content: ByteArray,
    val documentTypeId: UUID?,
    val teamId: UUID?
)
