package at.fhtw.swen3.paperless.exception

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.net.URI

class GlobalExceptionHandlerTest {
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
        val exception = NoResourceFoundException(HttpMethod.GET, "/users/", "/users/")

        val response = handler.handleNoResourceFound(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.type).isEqualTo(URI.create("urn:problem-type:404"))
        assertThat(response.body?.title).isEqualTo("Resource Not Found")
        assertThat(response.body?.status).isEqualTo(404)
        assertThat(response.body?.detail).isEqualTo("The requested resource was not found.")
        assertThat(response.body?.detail).doesNotContain("/users/")

        val annotation = GlobalExceptionHandler::class.java.getMethod(
            "handleNoResourceFound",
            NoResourceFoundException::class.java
        ).getAnnotation(ExceptionHandler::class.java)
        assertThat(annotation.value).containsExactly(NoResourceFoundException::class)
    }
}
