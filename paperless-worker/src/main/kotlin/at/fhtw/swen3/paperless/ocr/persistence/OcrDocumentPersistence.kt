package at.fhtw.swen3.paperless.ocr.persistence

import at.fhtw.swen3.paperless.ocr.model.ProcessingStatus
import at.fhtw.swen3.paperless.ocr.model.WorkerDocument
import java.util.UUID

interface OcrDocumentPersistence {
    fun claim(documentId: UUID): WorkerDocument?

    fun complete(documentId: UUID, ocrContent: String, status: ProcessingStatus)

    fun fail(documentId: UUID)
}
