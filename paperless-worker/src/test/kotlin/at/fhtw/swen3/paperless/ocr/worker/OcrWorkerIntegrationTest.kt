package at.fhtw.swen3.paperless.ocr.worker

import at.fhtw.swen3.paperless.ocr.config.OcrMessagingProperties
import at.fhtw.swen3.paperless.ocr.engine.OcrEngine
import at.fhtw.swen3.paperless.ocr.engine.OcrEngineException
import at.fhtw.swen3.paperless.ocr.entity.WorkerDocumentEntity
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.model.ProcessingStatus
import at.fhtw.swen3.paperless.ocr.persistence.DocumentIndexPersistence
import at.fhtw.swen3.paperless.ocr.persistence.repository.OcrDocumentRepository
import at.fhtw.swen3.paperless.ocr.service.OcrProcessingService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.timeout
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

@SpringBootTest(
    properties = [
        "spring.config.location=file:src/main/resources/application.yaml",
        "spring.datasource.url=jdbc:h2:mem:ocr-rabbit-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.rabbitmq.listener.simple.retry.initial-interval=10ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=10ms",
        "paperless.worker.enabled=true",
    ],
)
@EnabledIfEnvironmentVariable(named = "PAPERLESS_RABBITMQ_TEST", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OcrWorkerIntegrationTest {
    @Autowired
    lateinit var template: RabbitTemplate

    @Autowired
    lateinit var admin: RabbitAdmin

    @Autowired
    lateinit var registry: RabbitListenerEndpointRegistry

    @Autowired
    lateinit var properties: OcrMessagingProperties

    @Autowired
    lateinit var documents: OcrDocumentRepository

    @Autowired
    lateinit var jsonMapper: JsonMapper

    @MockitoBean
    lateinit var engine: OcrEngine

    @MockitoBean
    lateinit var index: DocumentIndexPersistence

    @MockitoSpyBean
    lateinit var service: OcrProcessingService

    @AfterAll
    fun cleanUp() {
        registry.stop()
        admin.deleteQueue(properties.queue)
        admin.deleteQueue(properties.deadLetterQueue)
        admin.deleteExchange(properties.exchange)
        admin.deleteExchange(properties.deadLetterExchange)
    }

    @Test
    fun backend_message_is_consumed_and_duplicate_is_skipped_ok() {
        // given
        val request = pendingRequest()
        val source = source(request)
        `when`(engine.extractText(source)).thenReturn("INVOICE #9821")

        // when
        template.convertAndSend(properties.exchange, properties.routingKey, request)

        // then
        awaitCompleted(request)
        assertThat(documents.findById(request.documentId).orElseThrow().ocrContent).isEqualTo("INVOICE #9821")
        verify(index).index(request.documentId, "INVOICE #9821")

        // when
        template.convertAndSend(properties.exchange, properties.routingKey, request)

        // then
        verify(service, timeout(TEST_TIMEOUT.toMillis()).times(2)).process(request)
        val listener = requireNotNull(registry.getListenerContainer("ocrWorker"))
        listener.stop()
        try {
            assertThat(requireNotNull(admin.getQueueInfo(properties.queue)).messageCount).isZero()
            verify(engine).extractText(source)
        } finally {
            listener.start()
        }
    }

    @Test
    fun json_without_java_type_headers_is_consumed_ok() {
        // given
        val request = pendingRequest()
        `when`(engine.extractText(source(request))).thenReturn("text")
        val message = Message(jsonMapper.writeValueAsBytes(request), jsonProperties())

        // when
        template.send(properties.exchange, properties.routingKey, message)

        // then
        awaitCompleted(request)
        verify(index).index(request.documentId, "text")
    }

    @Test
    fun transient_failure_retries_in_a_new_transaction_ok() {
        // given
        val request = pendingRequest()
        `when`(engine.extractText(source(request)))
            .thenThrow(OcrEngineException("temporary failure"))
            .thenReturn("recovered")

        // when
        template.convertAndSend(properties.exchange, properties.routingKey, request)

        // then
        awaitCompleted(request)
        verify(engine, times(2)).extractText(source(request))
        verify(index).index(request.documentId, "recovered")
        assertThat(documents.findById(request.documentId).orElseThrow().version).isEqualTo(4)
    }

    @Test
    fun exhausted_retries_commit_failed_status_and_dead_letter_message_ko() {
        // given
        val request = pendingRequest()
        `when`(engine.extractText(source(request))).thenThrow(OcrEngineException("permanent failure"))

        // when
        template.convertAndSend(properties.exchange, properties.routingKey, request)

        // then
        val rejected = requireNotNull(template.receive(properties.deadLetterQueue, TEST_TIMEOUT.toMillis()))
        assertThat(jsonMapper.readValue(rejected.body, OcrRequest::class.java)).isEqualTo(request)
        val document = documents.findById(request.documentId).orElseThrow()
        assertThat(document.status).isEqualTo(ProcessingStatus.FAILED)
        assertThat(document.version).isEqualTo(8)
        verify(engine, times(4)).extractText(source(request))
        verifyNoInteractions(index)
    }

    @Test
    fun invalid_json_is_dead_lettered_without_processing_ko() {
        // given
        val message = Message("{invalid".toByteArray(), jsonProperties())

        // when
        template.send(properties.exchange, properties.routingKey, message)

        // then
        val rejected = requireNotNull(template.receive(properties.deadLetterQueue, TEST_TIMEOUT.toMillis()))
        assertThat(rejected.body).isEqualTo(message.body)
        verifyNoInteractions(engine, index)
    }

    @Test
    fun unhandled_failure_rolls_back_claim_and_allows_redelivery_ko() {
        // given
        val request = pendingRequest()
        val failure = AssertionError("interrupted attempt")
        `when`(engine.extractText(source(request))).thenThrow(failure).thenReturn("recovered")

        // when / then
        assertThatThrownBy { service.process(request) }.isSameAs(failure)
        val document = documents.findById(request.documentId).orElseThrow()
        assertThat(document.status).isEqualTo(ProcessingStatus.PENDING)
        assertThat(document.version).isZero()

        // when
        template.convertAndSend(properties.exchange, properties.routingKey, request)

        // then
        awaitCompleted(request)
        verify(engine, times(2)).extractText(source(request))
    }

    private fun pendingRequest(): OcrRequest {
        val documentId = UUID.randomUUID()
        documents.saveAndFlush(WorkerDocumentEntity(id = documentId))

        return OcrRequest(UUID.randomUUID(), documentId, "documents/$documentId.pdf", "application/pdf")
    }

    private fun source(request: OcrRequest): Path =
        Path.of("/tmp/paperless-documents").resolve(request.storageKey.substringAfterLast('/'))

    private fun jsonProperties(): MessageProperties = MessageProperties().apply { contentType = "application/json" }

    private fun awaitCompleted(request: OcrRequest) {
        await().atMost(TEST_TIMEOUT).untilAsserted {
            assertThat(
                documents.findById(request.documentId).orElseThrow().status,
            ).isEqualTo(ProcessingStatus.COMPLETED)
        }
    }

    companion object {
        private val TEST_TIMEOUT = Duration.ofSeconds(10)
        private val topologyName = "paperless.worker.test.${UUID.randomUUID()}"

        @JvmStatic
        @DynamicPropertySource
        fun rabbitProperties(registry: DynamicPropertyRegistry) {
            registry.add("paperless.ocr.exchange") { topologyName }
            registry.add("paperless.ocr.queue") { "$topologyName.requests" }
            registry.add("paperless.ocr.dead-letter-exchange") { "$topologyName.dlx" }
            registry.add("paperless.ocr.dead-letter-queue") { "$topologyName.failed" }
        }
    }
}
