package at.fhtw.swen3.paperless.ocr.messaging

import at.fhtw.swen3.paperless.ocr.model.OcrResult

interface OcrResultPublisher {
    fun publish(result: OcrResult)
}
