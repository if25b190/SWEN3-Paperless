package at.fhtw.swen3.paperless.exception

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.net.URI

@ExtendWith(OutputCaptureExtension::class)
class GlobalExceptionHandlerTest {
    @Test
    fun unexpected_failure_logs_diagnostics_without_sensitive_messages_ko(output: CapturedOutput) {
        // given
        val exception = IllegalStateException("password=private-password token=private-token")

        // when
        val response = GlobalExceptionHandler().handleUnexpectedException(exception)

        // then
        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(output.all).contains("[API] - ACTION: unexpectedFailure", "java.lang.IllegalStateException")
            .doesNotContain("private-password", "private-token")
    }

    @Test
    fun domain_failure_is_logged_once_at_the_boundary_ko(output: CapturedOutput) {
        // when
        GlobalExceptionHandler().handleAppException(UserNotFoundException())

        // then
        assertThat(Regex("\\[API] - ACTION: rejectRequest").findAll(output.all).count()).isEqualTo(1)
    }

    @Test
    fun oversized_uploads_return_payload_too_large_problem() {
        val handler = GlobalExceptionHandler()

        val response = handler.handleMaxUploadSizeExceeded()

        assertThat(response.statusCode).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
        assertThat(response.body?.type).isEqualTo(URI.create("urn:problem-type:413"))
        assertThat(response.body?.title).isEqualTo("Payload Too Large")
        assertThat(response.body?.status).isEqualTo(413)
        assertThat(response.body?.detail).isEqualTo("The uploaded content exceeds the maximum allowed size.")
        assertThat(response.body?.invalidParams).isNull()

        val annotation = GlobalExceptionHandler::class.java.getMethod(
            "handleMaxUploadSizeExceeded"
        ).getAnnotation(ExceptionHandler::class.java)
        assertThat(annotation.value).containsExactly(MaxUploadSizeExceededException::class)
    }

    @Test
    fun missing_resources_return_generic_not_found_problem() {
        val handler = GlobalExceptionHandler()
        val exception = NoResourceFoundException(HttpMethod.GET, "/api/v1/users/", "/api/v1/users/")

        val response = handler.handleNoResourceFound(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.type).isEqualTo(URI.create("urn:problem-type:404"))
        assertThat(response.body?.title).isEqualTo("Resource Not Found")
        assertThat(response.body?.status).isEqualTo(404)
        assertThat(response.body?.detail).isEqualTo("The requested resource was not found.")
        assertThat(response.body?.detail).doesNotContain("/api/v1/users/")

        val annotation = GlobalExceptionHandler::class.java.getMethod(
            "handleNoResourceFound",
            NoResourceFoundException::class.java
        ).getAnnotation(ExceptionHandler::class.java)
        assertThat(annotation.value).containsExactly(NoResourceFoundException::class)
    }
}
