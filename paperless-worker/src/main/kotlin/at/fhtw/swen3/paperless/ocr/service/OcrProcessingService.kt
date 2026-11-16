package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.ocr.model.DocumentDeletion
import at.fhtw.swen3.paperless.ocr.model.OcrRequest

interface OcrProcessingService {
    fun process(request: OcrRequest)

    fun processDeletion(deletion: DocumentDeletion)
}
