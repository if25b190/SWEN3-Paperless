package at.fhtw.swen3.paperless.ocr.worker

import at.fhtw.swen3.paperless.ocr.model.DocumentDeletion
import at.fhtw.swen3.paperless.ocr.model.OcrResult
import at.fhtw.swen3.paperless.ocr.service.OcrResultService
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class OcrResultWorker(private val service: OcrResultService) {
    @RabbitListener(id = "ocrResults", queues = ["\${paperless.ocr.result-queue:paperless.ocr.results}"])
    fun apply(result: OcrResult) {
        service.apply(result)
    }

    @RabbitListener(id = "documentDeletion", queues = ["\${paperless.ocr.deletion-queue:paperless.ocr.deletions}"])
    fun delete(deletion: DocumentDeletion) {
        service.processDeletion(deletion)
    }
}
