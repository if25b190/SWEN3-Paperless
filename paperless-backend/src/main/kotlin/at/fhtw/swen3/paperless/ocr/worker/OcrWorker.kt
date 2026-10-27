package at.fhtw.swen3.paperless.ocr.worker

import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class OcrWorker {
    @RabbitListener(
        queues = ["\${paperless.ocr.queue:paperless.ocr.requests}"],
        autoStartup = "\${paperless.ocr.worker-enabled:false}"
    )
    fun process(request: OcrRequest) {
        logger.info("[OCR] - ACTION: process: jobId: {}, documentId: {}", request.jobId, request.documentId)
        // TODO: Implement OCR processing and idempotency using request.jobId before enabling the worker.
    }

    private companion object {
        val logger = LoggerFactory.getLogger(OcrWorker::class.java)
    }
}
