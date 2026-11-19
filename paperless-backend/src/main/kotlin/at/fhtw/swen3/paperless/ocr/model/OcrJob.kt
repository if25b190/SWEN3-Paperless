package at.fhtw.swen3.paperless.ocr.model

import java.time.Instant
import java.util.UUID

data class OcrJob(
    val id: UUID?,
    val documentId: UUID,
    val storageKey: String,
    val contentType: String,
    val attemptId: UUID? = null,
    val attempts: Int = 0,
    val availableAt: Instant = Instant.EPOCH,
)
