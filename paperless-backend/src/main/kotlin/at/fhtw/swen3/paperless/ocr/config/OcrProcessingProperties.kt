package at.fhtw.swen3.paperless.ocr.config

import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.hibernate.validator.constraints.time.DurationMin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties("paperless.ocr")
data class OcrProcessingProperties(
    @field:DurationMin(nanos = 1)
    val leaseDuration: Duration = Duration.ofMinutes(10),
    @field:DurationMin(nanos = 1)
    val retryDelay: Duration = Duration.ofSeconds(5),
    @field:Positive
    val maxAttempts: Int = 4,
    val finalStatus: ProcessingStatus = ProcessingStatus.COMPLETED,
    @field:NotBlank
    val indexName: String = "paperless-documents",
) {
    @AssertTrue(message = "final status must be COMPLETED or GENAI_IN_PROGRESS")
    fun isFinalStatusValid(): Boolean =
        finalStatus == ProcessingStatus.COMPLETED || finalStatus == ProcessingStatus.GENAI_IN_PROGRESS
}
