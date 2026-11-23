package at.fhtw.swen3.paperless.ocr.worker

import at.fhtw.swen3.paperless.ocr.config.OcrMessagingProperties
import at.fhtw.swen3.paperless.ocr.engine.OcrEngine
import at.fhtw.swen3.paperless.ocr.engine.OcrEngineException
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.model.OcrResult
import at.fhtw.swen3.paperless.ocr.model.OcrResultStatus
import at.fhtw.swen3.paperless.ocr.persistence.DocumentFilePersistence
import at.fhtw.swen3.paperless.testsupport.any
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageDeliveryMode
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

@SpringBootTest(
    properties = [
        "spring.config.location=file:src/main/resources/application.yaml",
        "spring.rabbitmq.listener.simple.retry.initial-interval=10ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=10ms",
        "paperless.worker.enabled=true",
    ],
)
@EnabledIfEnvironmentVariable(named = "PAPERLESS_RABBITMQ_TEST", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OcrWorkerIntegrationTest {
    @Autowired lateinit var template: RabbitTemplate

    @Autowired lateinit var admin: RabbitAdmin

    @Autowired lateinit var registry: RabbitListenerEndpointRegistry

    @Autowired lateinit var properties: OcrMessagingProperties

    @Autowired lateinit var jsonMapper: JsonMapper

    @MockitoBean lateinit var engine: OcrEngine

    @MockitoBean(enforceOverride = true)
    lateinit var files: DocumentFilePersistence

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
    fun backend_json_without_type_headers_produces_confirmed_results_ok() {
        // given
        val request = request()
        `when`(engine.extractText(any())).thenReturn("INVOICE #9821")
        // when
        template.send(properties.exchange, properties.routingKey, jsonMessage(request))
        // then
        assertThat(receiveResult()).isEqualTo(result(request, OcrResultStatus.STARTED))
        assertThat(receiveResult()).isEqualTo(result(request, OcrResultStatus.SUCCEEDED, "INVOICE #9821"))
    }

    @Test
    fun ocr_failure_is_reported_for_backend_retry_without_reprocessing_locally_ok() {
        // given
        val request = request()
        `when`(engine.extractText(any())).thenThrow(OcrEngineException("tool unavailable"))
        // when
        template.send(properties.exchange, properties.routingKey, jsonMessage(request))
        // then
        assertThat(receiveResult()).isEqualTo(result(request, OcrResultStatus.STARTED))
        assertThat(receiveResult()).isEqualTo(result(request, OcrResultStatus.FAILED))
    }

    @Test
    fun invalid_json_is_dead_lettered_without_processing_ko() {
        // given
        val message = Message("{invalid".toByteArray(), jsonProperties())
        // when
        template.send(properties.exchange, properties.routingKey, message)
        // then
        val rejected = requireNotNull(template.receive(properties.deadLetterQueue, 10000))
        assertThat(rejected.body).isEqualTo(message.body)
        verifyNoInteractions(engine, files)
    }

    private fun receiveResult(): OcrResult {
        val message = requireNotNull(template.receive(properties.resultQueue, 10000))
        assertThat(message.messageProperties.receivedDeliveryMode).isEqualTo(MessageDeliveryMode.PERSISTENT)
        val result = jsonMapper.readValue(message.body, OcrResult::class.java)
        assertThat(message.messageProperties.messageId).isEqualTo("${result.attemptId}:${result.status}")

        return result
    }

    private fun request() = OcrRequest(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "documents/${UUID.randomUUID()}.pdf",
        "application/pdf",
        UUID.randomUUID(),
    )
    private fun result(request: OcrRequest, status: OcrResultStatus, text: String? = null) =
        OcrResult(request.jobId, request.documentId, request.attemptId, status, text)
    private fun jsonMessage(request: OcrRequest) = Message(jsonMapper.writeValueAsBytes(request), jsonProperties())
    private fun jsonProperties() = MessageProperties().apply { contentType = "application/json" }

    companion object {
        private val name = "paperless.worker.test.${UUID.randomUUID()}"

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
