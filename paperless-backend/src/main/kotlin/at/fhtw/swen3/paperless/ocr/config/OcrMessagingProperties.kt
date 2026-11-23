package at.fhtw.swen3.paperless.ocr.config

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.hibernate.validator.constraints.time.DurationMin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties("paperless.ocr")
data class OcrMessagingProperties(
    @field:NotBlank
    val exchange: String = "paperless.ocr",
    @field:NotBlank
    val queue: String = "paperless.ocr.requests",
    @field:NotBlank
    val routingKey: String = "document.uploaded",
    @field:NotBlank
    val deletionQueue: String = "paperless.ocr.deletions",
    @field:NotBlank
    val deletionRoutingKey: String = "document.deleted",
    @field:NotBlank
    val resultQueue: String = "paperless.ocr.results",
    @field:NotBlank
    val resultRoutingKey: String = "document.ocr.result",
    @field:NotBlank
    val deadLetterExchange: String = "paperless.ocr.dead-letter",
    @field:NotBlank
    val deadLetterQueue: String = "paperless.ocr.failed",
    @field:NotNull
    @field:DurationMin(nanos = 1, message = "must be a positive duration")
    val confirmTimeout: Duration = Duration.ofSeconds(5)
)
