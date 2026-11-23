package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.ocr.model.DocumentDeletion
import at.fhtw.swen3.paperless.ocr.model.OcrResult

interface OcrResultService {
    fun apply(result: OcrResult)
    fun processDeletion(deletion: DocumentDeletion)
}
