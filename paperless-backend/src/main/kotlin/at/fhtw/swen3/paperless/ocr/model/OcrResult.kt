package at.fhtw.swen3.paperless.ocr.model

import java.util.UUID

data class OcrResult(
    val jobId: UUID,
    val documentId: UUID,
    val attemptId: UUID,
    val status: OcrResultStatus,
    val ocrContent: String? = null,
)

enum class OcrResultStatus { STARTED, SUCCEEDED, FAILED }
