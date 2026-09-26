package at.fhtw.swen3.paperless.document.service

import at.fhtw.swen3.paperless.exception.GlobalExceptionHandler
import jakarta.persistence.OptimisticLockException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.orm.ObjectOptimisticLockingFailureException

class DocumentConflictHandlerTest {
    @Test
    fun optimistic_document_conflicts_return_409() {
        val handler = GlobalExceptionHandler()
        assertThat(handler.handleConflict().statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(handler.handleConflict().body?.status).isEqualTo(409)
        // Both JPA and Spring optimistic failures are declared on the same handler.
        val annotation = GlobalExceptionHandler::class.java.getMethod("handleConflict")
            .getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler::class.java)
        assertThat(annotation.value).contains(OptimisticLockException::class, ObjectOptimisticLockingFailureException::class)
    }
}
