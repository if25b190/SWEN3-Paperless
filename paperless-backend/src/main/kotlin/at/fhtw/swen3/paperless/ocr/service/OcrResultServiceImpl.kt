package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.persistence.DocumentPersistence
import at.fhtw.swen3.paperless.ocr.config.OcrProcessingProperties
import at.fhtw.swen3.paperless.ocr.model.DocumentDeletion
import at.fhtw.swen3.paperless.ocr.model.OcrResult
import at.fhtw.swen3.paperless.ocr.model.OcrResultStatus
import at.fhtw.swen3.paperless.ocr.persistence.DocumentIndexPersistence
import at.fhtw.swen3.paperless.ocr.persistence.OcrJobPersistence
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class OcrResultServiceImpl(
    private val documents: DocumentPersistence,
    private val jobs: OcrJobPersistence,
    private val index: DocumentIndexPersistence,
    private val properties: OcrProcessingProperties,
) : OcrResultService {
    override fun apply(result: OcrResult) {
        // Lock in document -> job order, matching dispatch and document deletion.
        val document = documents.findLockedById(result.documentId) ?: return
        val job = jobs.findLockedById(result.jobId) ?: return
        if (job.documentId != result.documentId || job.attemptId != result.attemptId) return

        when (result.status) {
            OcrResultStatus.STARTED -> {
                if (document.status == ProcessingStatus.OCR_IN_PROGRESS) return

                documents.update(result.documentId, document.copy(status = ProcessingStatus.OCR_IN_PROGRESS))
            }
            OcrResultStatus.SUCCEEDED -> {
                val text = requireNotNull(result.ocrContent) { "Successful OCR result must contain text" }
                index.index(result.documentId, text)
                documents.update(
                    result.documentId,
                    document.copy(status = properties.finalStatus, ocrContent = text),
                )
                jobs.delete(result.jobId)
            }
            OcrResultStatus.FAILED -> {
                documents.update(result.documentId, document.copy(status = ProcessingStatus.FAILED))
                if (job.attempts >= properties.maxAttempts) {
                    jobs.delete(result.jobId)
                } else {
                    jobs.update(job.copy(attemptId = null, availableAt = Instant.now().plus(properties.retryDelay)))
                }
            }
        }
        logger.info(
            "[OCR] - ACTION: applyResult: jobId: {}, documentId: {}, attemptId: {}, status: {}",
            result.jobId,
            result.documentId,
            result.attemptId,
            result.status,
        )
    }

    override fun processDeletion(deletion: DocumentDeletion) {
        index.delete(deletion.documentId)
        logger.info("[OCR] - ACTION: processDeletion: documentId: {}", deletion.documentId)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(OcrResultServiceImpl::class.java)
    }
}
