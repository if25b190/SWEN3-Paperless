package at.fhtw.swen3.paperless.ocr.worker

import at.fhtw.swen3.paperless.ocr.model.OcrResult
import at.fhtw.swen3.paperless.ocr.service.OcrResultService
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class OcrResultListener(private val results: OcrResultService) {
    @RabbitListener(queues = ["\${paperless.ocr.result-queue:paperless.ocr.results}"])
    fun receive(result: OcrResult) {
        try {
            results.apply(result)
        } catch (exception: IllegalArgumentException) {
            throw AmqpRejectAndDontRequeueException("Invalid OCR result.", exception)
        }
    }
}
