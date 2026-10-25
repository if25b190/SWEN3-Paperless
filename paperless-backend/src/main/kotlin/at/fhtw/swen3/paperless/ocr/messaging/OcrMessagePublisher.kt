package at.fhtw.swen3.paperless.ocr.messaging

import at.fhtw.swen3.paperless.ocr.model.OcrRequest

interface OcrMessagePublisher {
    fun publish(request: OcrRequest)
}
