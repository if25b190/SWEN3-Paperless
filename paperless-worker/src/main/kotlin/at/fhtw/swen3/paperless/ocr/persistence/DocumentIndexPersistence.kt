package at.fhtw.swen3.paperless.ocr.persistence

import java.util.UUID

interface DocumentIndexPersistence {

    fun index(documentId: UUID, ocrContent: String)
}
