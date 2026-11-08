package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import at.fhtw.swen3.paperless.ocr.engine.OcrEngine
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.persistence.OcrDocumentPersistence
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OcrProcessingServiceImpl(
    private val documents: OcrDocumentPersistence,
    private val engine: OcrEngine,
    private val properties: OcrWorkerProperties
) : OcrProcessingService {
    override fun process(request: OcrRequest) {
        val claimed = documents.claim(request.documentId)
        if (claimed == null) {
            logger.info(
                "[OCR] - ACTION: process: skipped duplicate: jobId: {}, documentId: {}",
                request.jobId,
                request.documentId
            )
            return
        }
        val source = properties.storageDir.resolve(request.storageKey.substringAfterLast('/'))
        try {
            val text = engine.extractText(source)
            documents.complete(request.documentId, text, properties.finalStatus)
            logger.info(
                "[OCR] - ACTION: process: completed: jobId: {}, documentId: {}, textLength: {}, finalStatus: {}",
                request.jobId,
                request.documentId,
                text.length,
                properties.finalStatus
            )
        } catch (exception: Exception) {
            markFailed(request.documentId)
            logger.error(
                "[OCR] - ACTION: process: failed: jobId: {}, documentId: {}, exceptionType: {}",
                request.jobId,
                request.documentId,
                exception.javaClass.name
            )
            throw exception
        }
    }

    private fun markFailed(documentId: UUID) {
        try {
            documents.fail(documentId)
        } catch (exception: Exception) {
            logger.warn(
                "[OCR] - ACTION: markFailed: documentId: {}, exceptionType: {}",
                documentId,
                exception.javaClass.name
            )
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(OcrProcessingServiceImpl::class.java)
    }
}
