package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.ocr.engine.OcrEngine
import at.fhtw.swen3.paperless.ocr.messaging.OcrResultPublisher
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.model.OcrResult
import at.fhtw.swen3.paperless.ocr.model.OcrResultStatus
import at.fhtw.swen3.paperless.ocr.persistence.DocumentFilePersistence
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.FileNotFoundException

@Service
class OcrProcessingServiceImpl(
    private val publisher: OcrResultPublisher,
    private val engine: OcrEngine,
    private val files: DocumentFilePersistence,
) : OcrProcessingService {
    override fun process(request: OcrRequest) {
        publisher.publish(result(request, OcrResultStatus.STARTED))
        val text = try {
            files.withLocalFile(request.storageKey, engine::extractText)
                ?: throw FileNotFoundException("OCR source file is missing")
        } catch (exception: Exception) {
            publisher.publish(result(request, OcrResultStatus.FAILED))
            logger.warn(
                "[OCR] - ACTION: processFailed: jobId: {}, documentId: {}, attemptId: {}, exceptionType: {}",
                request.jobId,
                request.documentId,
                request.attemptId,
                exception.javaClass.name,
            )

            return
        }
        // A publication failure must propagate for broker redelivery, never become an OCR failure result.
        publisher.publish(result(request, OcrResultStatus.SUCCEEDED).copy(ocrContent = text))
        logger.info(
            "[OCR] - ACTION: processCompleted: jobId: {}, documentId: {}, attemptId: {}, textLength: {}",
            request.jobId,
            request.documentId,
            request.attemptId,
            text.length,
        )
    }

    private fun result(request: OcrRequest, status: OcrResultStatus): OcrResult =
        OcrResult(request.jobId, request.documentId, request.attemptId, status)

    private companion object {
        val logger = LoggerFactory.getLogger(OcrProcessingServiceImpl::class.java)
    }
}
