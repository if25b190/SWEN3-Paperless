package at.fhtw.swen3.paperless.ocr.messaging

import at.fhtw.swen3.paperless.exception.OcrPublishException
import at.fhtw.swen3.paperless.ocr.config.OcrMessagingProperties
import at.fhtw.swen3.paperless.ocr.model.DocumentDeletion
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
        send(properties.routingKey, request.jobId.toString(), request)
    }

    override fun publishDeletion(documentId: UUID) {
        send(properties.deletionRoutingKey, documentId.toString(), DocumentDeletion(documentId))
    }

    private fun send(routingKey: String, messageId: String, payload: Any) {
        val correlation = CorrelationData(UUID.randomUUID().toString())
        try {
            template.convertAndSend(properties.exchange, routingKey, payload, { message ->
                message.messageProperties.messageId = messageId
                message.messageProperties.deliveryMode = MessageDeliveryMode.PERSISTENT
                message
            }, correlation)
            val confirm = correlation.future.get(properties.confirmTimeout.toMillis(), TimeUnit.MILLISECONDS)
            if (!confirm.ack() || correlation.returned != null) {
                throw OcrPublishException()
            }
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw OcrPublishException(exception)
        } catch (exception: AmqpException) {
            throw OcrPublishException(exception)
        } catch (exception: TimeoutException) {
            throw OcrPublishException(exception)
        } catch (exception: ExecutionException) {
            throw OcrPublishException(exception)
        }
    }
}
