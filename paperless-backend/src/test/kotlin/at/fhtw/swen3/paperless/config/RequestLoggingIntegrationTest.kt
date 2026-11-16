package at.fhtw.swen3.paperless.config

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest(properties = [
    "logging.level.at.fhtw.swen3.paperless=DEBUG"
])
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension::class)
class RequestLoggingIntegrationTest(@Autowired private val mvc: MockMvc) {
    private val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    private val events = object : ListAppender<ILoggingEvent>() {
        override fun append(event: ILoggingEvent) {
            event.prepareForDeferredProcessing()
            super.append(event)
        }
    }

    @BeforeEach
    fun capture_log_events() {
        events.start()
        logger.addAppender(events)
    }

    @AfterEach
    fun release_log_events() {
        logger.detachAppender(events)
        events.stop()
    }

    @Test
    fun rejected_requests_receive_distinct_server_generated_request_ids_ok(output: CapturedOutput) {
        // when
        val first = mvc.perform(get("/api/v1/documents")
            .header("X-Request-ID", "untrusted-client-id")
            .header("Authorization", "Bearer private-test-token")
            .param("query", "private-search-phrase"))
            .andExpect(status().isUnauthorized).andExpect(header().exists("X-Request-ID")).andReturn().response
        val second = mvc.perform(get("/api/v1/documents"))
            .andExpect(status().isUnauthorized).andReturn().response

        // then
        val requestId = UUID.fromString(first.getHeader("X-Request-ID")).toString()
        assertThat(requestId).isNotEqualTo("untrusted-client-id").isNotEqualTo(second.getHeader("X-Request-ID"))
        assertThat(securityRequestIds())
            .containsExactly(requestId, second.getHeader("X-Request-ID"))
        assertThat(output.all).contains("[SECURITY] - ACTION: rejectRequest")
            .doesNotContain("untrusted-client-id", "private-test-token", "private-search-phrase")
    }

    private fun securityRequestIds(): List<String?> = events.list
        .filter { it.formattedMessage.contains("[SECURITY] - ACTION: rejectRequest") }
        .map { it.mdcPropertyMap["requestId"] }
}
