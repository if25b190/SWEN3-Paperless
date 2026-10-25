package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.ocr.messaging.OcrMessagePublisher
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.persistence.OcrJobPersistence
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
        val jobId = requireNotNull(job.id)
        publisher.publish(OcrRequest(jobId, job.documentId, job.storageKey, job.contentType))
        jobs.delete(jobId)
    }
}
