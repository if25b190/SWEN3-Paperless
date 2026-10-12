package at.fhtw.swen3.paperless.config

import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID

class RequestLoggingFilterTest {

    @Test
    fun request_id_is_available_in_the_chain_and_cleared_after_completion_ok() {
        // given
        val request = MockHttpServletRequest().apply { addHeader("X-Request-ID", "untrusted-id") }
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ ->
            assertThat(MDC.get("requestId")).isEqualTo(response.getHeader("X-Request-ID"))
        }

        // when
        RequestLoggingFilter().doFilter(request, response, chain)

        // then
        val requestId = UUID.fromString(response.getHeader("X-Request-ID")).toString()
        assertThat(requestId).isNotEqualTo("untrusted-id")
        assertThat(MDC.get("requestId")).isNull()
    }

    @Test
    fun failed_requests_restore_the_previous_mdc_value_ko() {
        // given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> throw IllegalStateException("failure") }
        MDC.put("requestId", "outer-request")

        try {
            // when / then
            assertThatThrownBy { RequestLoggingFilter().doFilter(request, response, chain) }
                .isInstanceOf(IllegalStateException::class.java)
            assertThat(MDC.get("requestId")).isEqualTo("outer-request")
        } finally {
            MDC.remove("requestId")
        }
    }
}
