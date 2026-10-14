package at.fhtw.swen3.paperless.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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
    "logging.level.at.fhtw.swen3.paperless=DEBUG",
    "logging.pattern.level=%5p [requestId=%X{requestId:-none}]"
])
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension::class)
class RequestLoggingIntegrationTest(@Autowired private val mvc: MockMvc) {

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
        assertThat(output.all).contains("requestId=$requestId", "[SECURITY] - ACTION: rejectRequest")
            .doesNotContain("untrusted-client-id", "private-test-token", "private-search-phrase")
    }

}
