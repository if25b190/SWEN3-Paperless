package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.exception.OcrJobInvalidException
import at.fhtw.swen3.paperless.ocr.messaging.OcrMessagePublisher
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.persistence.OcrJobPersistence
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class OcrDispatchServiceImpl(
    private val jobs: OcrJobPersistence,
    private val publisher: OcrMessagePublisher
) : OcrDispatchService {
    override fun dispatchNext() {
        val job = jobs.findNextForDispatch() ?: return
        if (job.id == null) {
            throw OcrJobInvalidException()
        }
        val jobId = job.id
        publisher.publish(OcrRequest(jobId, job.documentId, job.storageKey, job.contentType))
        jobs.delete(jobId)
        logger.info("[OCR] - ACTION: dispatchNext: jobId: {}, documentId: {}", jobId, job.documentId)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(OcrDispatchServiceImpl::class.java)
    }
}
