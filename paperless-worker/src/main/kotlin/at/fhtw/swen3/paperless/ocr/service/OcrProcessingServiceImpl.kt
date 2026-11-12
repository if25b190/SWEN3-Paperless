package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import at.fhtw.swen3.paperless.ocr.engine.OcrEngine
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.model.ProcessingStatus
import at.fhtw.swen3.paperless.ocr.model.WorkerDocument
import at.fhtw.swen3.paperless.ocr.persistence.DocumentIndexPersistence
import at.fhtw.swen3.paperless.ocr.persistence.OcrDocumentPersistence
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(noRollbackFor = [AppException::class])
class OcrProcessingServiceImpl(
    private val documents: OcrDocumentPersistence,
    private val engine: OcrEngine,
    private val index: DocumentIndexPersistence,
    private val properties: OcrWorkerProperties,
) : OcrProcessingService {
    override fun process(request: OcrRequest) {
        val document = documents.findLockedById(request.documentId) ?: return
        val retryable = document.status == ProcessingStatus.PENDING || document.status == ProcessingStatus.FAILED
        if (!retryable) {
            logger.info(
                "[OCR] - ACTION: process: skipped duplicate: jobId: {}, documentId: {}",
                request.jobId,
                request.documentId,
            )

            return
        }
        val claimed = documents.update(document.copy(status = ProcessingStatus.OCR_IN_PROGRESS, ocrContent = null))
        val source = properties.storageDir.resolve(request.storageKey.substringAfterLast('/'))
        val text = try {
            val extracted = engine.extractText(source)
            index.index(request.documentId, extracted)
            extracted
        } catch (exception: Exception) {
            markFailed(claimed, exception)
            throw AppException(AppErrorMessage.OCR_PROCESSING_FAILED, exception)
        }
        documents.update(claimed.copy(status = properties.finalStatus, ocrContent = text))
        logger.info(
            "[OCR] - ACTION: process: completed: jobId: {}, documentId: {}, textLength: {}, finalStatus: {}",
            request.jobId,
            request.documentId,
            text.length,
            properties.finalStatus,
        )
    }

    private fun markFailed(document: WorkerDocument, failure: Exception) {
        try {
            documents.update(document.copy(status = ProcessingStatus.FAILED, ocrContent = null))
        } catch (exception: Exception) {
            exception.addSuppressed(failure)
            throw exception
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(OcrProcessingServiceImpl::class.java)
    }
}
