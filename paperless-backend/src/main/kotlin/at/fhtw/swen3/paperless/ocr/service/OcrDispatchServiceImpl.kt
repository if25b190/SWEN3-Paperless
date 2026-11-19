package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.persistence.DocumentPersistence
import at.fhtw.swen3.paperless.exception.OcrJobInvalidException
import at.fhtw.swen3.paperless.ocr.config.OcrProcessingProperties
import at.fhtw.swen3.paperless.ocr.messaging.OcrMessagePublisher
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.persistence.OcrJobPersistence
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class OcrDispatchServiceImpl(
    private val jobs: OcrJobPersistence,
    private val publisher: OcrMessagePublisher,
    private val documents: DocumentPersistence,
    private val properties: OcrProcessingProperties,
) : OcrDispatchService {
    override fun dispatchNext() {
        val candidate = jobs.findNextForDispatch() ?: return
        val jobId = candidate.id ?: throw OcrJobInvalidException()
        // Select without a lock, then always acquire document -> job locks.
        val document = documents.findLockedById(candidate.documentId) ?: return
        val job = jobs.findLockedById(jobId) ?: return
        val now = Instant.now()
        if (job.availableAt.isAfter(now)) return

        if (job.attempts >= properties.maxAttempts) {
            documents.update(job.documentId, document.copy(status = ProcessingStatus.FAILED))
            jobs.delete(jobId)

            return
        }
        val attemptId = UUID.randomUUID()
        jobs.update(
            job.copy(attemptId = attemptId, attempts = job.attempts + 1, availableAt = now.plus(properties.leaseDuration)),
        )
        publisher.publish(OcrRequest(jobId, job.documentId, job.storageKey, job.contentType, attemptId))
        logger.info("[OCR] - ACTION: dispatchNext: jobId: {}, documentId: {}, attemptId: {}", jobId, job.documentId, attemptId)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(OcrDispatchServiceImpl::class.java)
    }
}
