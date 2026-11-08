package at.fhtw.swen3.paperless.ocr.worker

import at.fhtw.swen3.paperless.ocr.dto.OcrRequestDTO
import at.fhtw.swen3.paperless.ocr.mapper.OcrRequestMapper
import at.fhtw.swen3.paperless.ocr.service.OcrProcessingService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class OcrWorker(
    private val service: OcrProcessingService,
    private val mapper: OcrRequestMapper
) {
    @RabbitListener(
        queues = ["\${paperless.ocr.queue:paperless.ocr.requests}"],
        autoStartup = "\${paperless.worker.enabled:true}"
    )
    fun process(dto: OcrRequestDTO) {
        val request = mapper.fromDto(dto)
        logger.info("[OCR] - ACTION: process: jobId: {}, documentId: {}", request.jobId, request.documentId)
        service.process(request)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(OcrWorker::class.java)
    }
}
