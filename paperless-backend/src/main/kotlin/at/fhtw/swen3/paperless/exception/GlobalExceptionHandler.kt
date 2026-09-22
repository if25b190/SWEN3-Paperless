package at.fhtw.swen3.paperless.exception

import at.fhtw.swen3.paperless.dto.InvalidParam
import at.fhtw.swen3.paperless.dto.Problem
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(AppException::class)
    fun handleAppException(exception: AppException): ResponseEntity<Problem> {
        logger.warn("Application exception: {}", exception.error)

        return problem(exception.error)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(exception: MethodArgumentNotValidException): ResponseEntity<Problem> {
        val invalidParams = exception.bindingResult.fieldErrors.map {
            InvalidParam(it.field, it.defaultMessage ?: "Invalid value.")
        }

        return problem(
            status = HttpStatus.BAD_REQUEST,
            title = "Bad Request",
            detail = "One or more request fields are invalid.",
            invalidParams = invalidParams
        )
    }

    @ExceptionHandler(ConstraintViolationException::class, HttpMessageNotReadableException::class)
    fun handleBadRequest(exception: Exception): ResponseEntity<Problem> =
        problem(
            status = HttpStatus.BAD_REQUEST,
            title = "Bad Request",
            detail = exception.message ?: "The request is invalid."
        )

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleConflict(): ResponseEntity<Problem> =
        problem(
            status = HttpStatus.CONFLICT,
            title = "Conflict",
            detail = "The request conflicts with an existing resource."
        )

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(exception: Exception): ResponseEntity<Problem> {
        logger.error("Unhandled application exception", exception)

        return problem(AppErrorMessage.INTERNAL_SERVER_ERROR)
    }

    private fun problem(error: AppErrorMessage): ResponseEntity<Problem> =
        problem(error.status, error.title, error.detail)

    private fun problem(
        status: HttpStatus,
        title: String,
        detail: String,
        invalidParams: List<InvalidParam>? = null
    ): ResponseEntity<Problem> {
        val response = Problem(
            type = URI.create("urn:problem-type:${status.value()}"),
            title = title,
            status = status.value(),
            detail = detail,
            invalidParams = invalidParams
        )

        return ResponseEntity.status(status).body(response)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
