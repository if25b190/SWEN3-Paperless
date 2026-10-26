package at.fhtw.swen3.paperless.ocr.messaging

import at.fhtw.swen3.paperless.ocr.config.OcrMessagingProperties
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.MessageDeliveryMode
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class RabbitOcrMessagePublisher(
    private val template: RabbitTemplate,
    private val properties: OcrMessagingProperties
) : OcrMessagePublisher {
    override fun publish(request: OcrRequest) {
        val correlation = CorrelationData(UUID.randomUUID().toString())
        try {
            template.convertAndSend(properties.exchange, properties.routingKey, request, { message ->
                message.messageProperties.messageId = request.jobId.toString()
                message.messageProperties.deliveryMode = MessageDeliveryMode.PERSISTENT
                message
            }, correlation)
            val confirm = correlation.future.get(properties.confirmTimeout.toMillis(), TimeUnit.MILLISECONDS)
            if (!confirm.ack() || correlation.returned != null) {
                throw IllegalStateException("OCR request could not be confirmed by the broker.", IllegalStateException("Broker rejected or returned the OCR request."))
            }
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("OCR request could not be confirmed by the broker.", exception)
        } catch (exception: AmqpException) {
            throw IllegalStateException("OCR request could not be confirmed by the broker.", exception)
        } catch (exception: TimeoutException) {
            throw IllegalStateException("OCR request could not be confirmed by the broker.", exception)
        } catch (exception: ExecutionException) {
            throw IllegalStateException("OCR request could not be confirmed by the broker.", exception)
        }
    }
}
