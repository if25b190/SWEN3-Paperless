package at.fhtw.swen3.paperless.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID
import java.util.concurrent.TimeUnit

class RequestLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val previousRequestId = MDC.get(REQUEST_ID)
        val requestId = UUID.randomUUID().toString()
        val started = System.nanoTime()
        MDC.put(REQUEST_ID, requestId)
        response.setHeader("X-Request-ID", requestId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            log.debug(
                "[HTTP] - ACTION: requestFinished: status: {}, durationMs: {}",
                response.status,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            )

            if (previousRequestId == null) {
                MDC.remove(REQUEST_ID)
            } else {
                MDC.put(REQUEST_ID, previousRequestId)
            }
        }
    }

    private companion object {
        const val REQUEST_ID = "requestId"
    }
}
