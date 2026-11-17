package at.fhtw.swen3.paperless.ocr.messaging

import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import java.util.UUID

interface OcrMessagePublisher {
    fun publish(request: OcrRequest)

    fun publishDeletion(documentId: UUID)
}
