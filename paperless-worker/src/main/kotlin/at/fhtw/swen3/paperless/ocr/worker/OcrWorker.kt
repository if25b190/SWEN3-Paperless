package at.fhtw.swen3.paperless.ocr.worker

import at.fhtw.swen3.paperless.ocr.dto.OcrRequestDTO
import at.fhtw.swen3.paperless.ocr.mapper.OcrRequestMapper
import at.fhtw.swen3.paperless.ocr.model.DocumentDeletion
import at.fhtw.swen3.paperless.ocr.service.OcrProcessingService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class OcrWorker(private val service: OcrProcessingService, private val mapper: OcrRequestMapper) {
    @RabbitListener(id = "ocrWorker", queues = ["\${paperless.ocr.queue:paperless.ocr.requests}"])
    fun process(dto: OcrRequestDTO) {
        val request = mapper.fromDto(dto)
        logger.info("[OCR] - ACTION: process: jobId: {}, documentId: {}", request.jobId, request.documentId)
        service.process(request)
    }

    @RabbitListener(
        id = "documentDeletionWorker",
        queues = ["\${paperless.ocr.deletion-queue:paperless.ocr.deletions}"],
    )
    fun delete(deletion: DocumentDeletion) {
        logger.info("[OCR] - ACTION: delete: documentId: {}", deletion.documentId)
        service.processDeletion(deletion)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(OcrWorker::class.java)
    }
}
