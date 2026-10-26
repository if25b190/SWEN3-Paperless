package at.fhtw.swen3.paperless.ocr.model

import java.util.UUID

data class OcrResult(
    val jobId: UUID,
    val documentId: UUID,
    val storageKey: String,
    val contentType: String,
    val status: OcrResultStatus,
    val text: String? = null,
    val extractedAt: String? = null,
    val errorCode: String? = null
)

enum class OcrResultStatus {
    COMPLETED,
    FAILED
}
