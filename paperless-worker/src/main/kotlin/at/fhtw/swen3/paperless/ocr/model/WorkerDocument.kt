package at.fhtw.swen3.paperless.ocr.model

import java.util.UUID

data class WorkerDocument(
    val id: UUID,
    val status: ProcessingStatus,
    val ocrContent: String?,
    val version: Long
)
