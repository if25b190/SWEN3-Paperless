package at.fhtw.swen3.paperless.ocr.messaging

import at.fhtw.swen3.paperless.exception.OcrPublishException
import at.fhtw.swen3.paperless.ocr.config.OcrMessagingProperties
import at.fhtw.swen3.paperless.ocr.config.OcrRabbitConfiguration
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.amqp.core.MessageDeliveryMode
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "PAPERLESS_RABBITMQ_TEST", matches = "true")
class RabbitOcrMessagePublisherIntegrationTest {
    @Test
    fun publishes_json_to_durable_queue_and_detects_missing_binding_ok() {
        // given
        val name = "paperless.test.${UUID.randomUUID()}"
        val properties = OcrMessagingProperties(
            exchange = name, queue = "$name.requests", deadLetterExchange = "$name.dlx", deadLetterQueue = "$name.failed"
        )
        val connection = CachingConnectionFactory(System.getenv("RABBITMQ_HOST") ?: "localhost")
        connection.username = System.getenv("RABBITMQ_USER") ?: "paperless"
        connection.setPassword(System.getenv("RABBITMQ_PASS") ?: "paperless_secure_password")
        connection.setPublisherConfirmType(ConfirmType.CORRELATED)
        connection.isPublisherReturns = true
        val admin = RabbitAdmin(connection)
        val topology = OcrRabbitConfiguration().ocrTopology(properties)
        admin.setAutoStartup(false)
        val context = org.springframework.context.support.GenericApplicationContext()
        context.beanFactory.registerSingleton("ocrTopology", topology)
        context.refresh()
        admin.setApplicationContext(context)
        try {
            admin.initialize()
            val template = RabbitTemplate(connection)
            template.setMandatory(true)
            val jsonMapper = JsonMapper.builder().findAndAddModules().build()
            template.messageConverter = OcrRabbitConfiguration().ocrMessageConverter(jsonMapper)
            val publisher = RabbitOcrMessagePublisher(template, properties)
            val request = OcrRequest(UUID.randomUUID(), UUID.randomUUID(), "documents/test.pdf", "application/pdf")

            // when
            publisher.publish(request)

            // then
            val message = requireNotNull(template.receive(properties.queue, 5000))
            assertThat(message.messageProperties.contentType).isEqualTo("application/json")
            assertThat(message.messageProperties.receivedDeliveryMode).isEqualTo(MessageDeliveryMode.PERSISTENT)
            assertThat(message.messageProperties.messageId).isEqualTo(request.jobId.toString())
            assertThat(jsonMapper.readValue(message.body, OcrRequest::class.java)).isEqualTo(request)
            assertThat(admin.getQueueInfo(properties.deadLetterQueue)).isNotNull()
            publisher.publish(request)
            template.execute { channel ->
                val delivery = requireNotNull(channel.basicGet(properties.queue, false))
                channel.basicReject(delivery.envelope.deliveryTag, false)
            }
            val rejected = requireNotNull(template.receive(properties.deadLetterQueue, 5000))
            assertThat(rejected.messageProperties.messageId).isEqualTo(request.jobId.toString())
            assertThatThrownBy {
                RabbitOcrMessagePublisher(template, properties.copy(routingKey = "missing.binding")).publish(request)
            }.isInstanceOf(OcrPublishException::class.java)
        } finally {
            admin.deleteQueue(properties.queue)
            admin.deleteQueue(properties.deadLetterQueue)
            admin.deleteExchange(properties.exchange)
            admin.deleteExchange(properties.deadLetterExchange)
            context.close()
            connection.destroy()
        }
    }
}
