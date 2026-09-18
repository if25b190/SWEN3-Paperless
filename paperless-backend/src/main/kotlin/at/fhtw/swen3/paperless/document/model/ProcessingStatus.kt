package at.fhtw.swen3.paperless.document.model

enum class ProcessingStatus {
    PENDING,
    OCR_IN_PROGRESS,
    GENAI_IN_PROGRESS,
    COMPLETED,
    FAILED
}
