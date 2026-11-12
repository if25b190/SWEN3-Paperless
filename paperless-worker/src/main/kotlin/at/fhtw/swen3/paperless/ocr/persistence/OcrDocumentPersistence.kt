package at.fhtw.swen3.paperless.ocr.persistence

import at.fhtw.swen3.paperless.ocr.model.WorkerDocument
import java.util.UUID

interface OcrDocumentPersistence {
    fun findLockedById(documentId: UUID): WorkerDocument?

    fun update(document: WorkerDocument): WorkerDocument
}
