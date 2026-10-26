package at.fhtw.swen3.paperless.ocr.model

import java.util.UUID

data class OcrRequest(
    val jobId: UUID,
    val documentId: UUID,
    val storageKey: String,
    val contentType: String
)
