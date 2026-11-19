package at.fhtw.swen3.paperless.ocr.worker

import at.fhtw.swen3.paperless.ocr.config.OcrMessagingProperties
import at.fhtw.swen3.paperless.ocr.model.DocumentDeletion
import at.fhtw.swen3.paperless.ocr.model.OcrResult
import at.fhtw.swen3.paperless.ocr.model.OcrResultStatus
import at.fhtw.swen3.paperless.ocr.service.OcrResultService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:ocr-results-rabbit;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.rabbitmq.dynamic=true",
    "spring.rabbitmq.listener.simple.auto-startup=true",
    "spring.rabbitmq.listener.simple.retry.enabled=true",
    "spring.rabbitmq.listener.simple.retry.initial-interval=10ms",
    "spring.rabbitmq.listener.simple.retry.max-interval=10ms",
])
@EnabledIfEnvironmentVariable(named = "PAPERLESS_RABBITMQ_TEST", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OcrResultWorkerIntegrationTest {
    @Autowired lateinit var template: RabbitTemplate
    @Autowired lateinit var admin: RabbitAdmin
    @Autowired lateinit var registry: RabbitListenerEndpointRegistry
    @Autowired lateinit var properties: OcrMessagingProperties
    @Autowired lateinit var jsonMapper: JsonMapper
    @MockitoBean lateinit var service: OcrResultService

    @AfterAll
    fun cleanUp() {
        registry.stop()
        admin.deleteQueue(properties.queue)
        admin.deleteQueue(properties.resultQueue)
        admin.deleteQueue(properties.deletionQueue)
        admin.deleteQueue(properties.deadLetterQueue)
        admin.deleteExchange(properties.exchange)
        admin.deleteExchange(properties.deadLetterExchange)
    }

    @Test
    fun worker_json_without_type_headers_reaches_backend_service_ok() {
        // given
        val result = result()
        // when
        send(result)
        // then
        verify(service, timeout(10000)).apply(result)
    }

    @Test
    fun failed_application_is_retried_by_result_listener_ok() {
        // given
        val result = result()
        doThrow(IllegalStateException("temporary persistence failure")).doNothing().`when`(service).apply(result)
        // when
        send(result)
        // then
        verify(service, timeout(10000).times(2)).apply(result)
    }

    @Test
    fun deletion_is_consumed_by_backend_ok() {
        // given
        val deletion = DocumentDeletion(UUID.randomUUID())
        // when
        template.convertAndSend(properties.exchange, properties.deletionRoutingKey, deletion)
        // then
        verify(service, timeout(10000)).processDeletion(deletion)
    }

    private fun send(result: OcrResult) {
        val properties = MessageProperties().apply { contentType = "application/json" }
        template.send(this.properties.exchange, this.properties.resultRoutingKey, Message(jsonMapper.writeValueAsBytes(result), properties))
    }

    private fun result() =
        OcrResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), OcrResultStatus.SUCCEEDED, "text")

    companion object {
        private val name = "paperless.results.test.${UUID.randomUUID()}"
        @JvmStatic
        @DynamicPropertySource
        fun rabbitProperties(registry: DynamicPropertyRegistry) {
            registry.add("paperless.ocr.exchange") { name }
            registry.add("paperless.ocr.queue") { "$name.requests" }
            registry.add("paperless.ocr.result-queue") { "$name.results" }
            registry.add("paperless.ocr.deletion-queue") { "$name.deletions" }
            registry.add("paperless.ocr.dead-letter-exchange") { "$name.dlx" }
            registry.add("paperless.ocr.dead-letter-queue") { "$name.failed" }
        }
    }
}
