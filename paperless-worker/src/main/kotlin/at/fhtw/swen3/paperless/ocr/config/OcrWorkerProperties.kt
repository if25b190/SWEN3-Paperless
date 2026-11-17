package at.fhtw.swen3.paperless.ocr.config

import at.fhtw.swen3.paperless.ocr.model.ProcessingStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.hibernate.validator.constraints.time.DurationMin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

@Validated
@ConfigurationProperties("paperless.worker")
data class OcrWorkerProperties(
    val enabled: Boolean = true,
    @field:NotNull
    val storageDir: Path = Paths.get("/tmp/paperless-documents"),
    @field:NotBlank
    val tesseractLanguages: String = "eng",
    @field:Positive
    val dpi: Int = 300,
    @field:NotNull
    @field:DurationMin(nanos = 1, message = "must be a positive duration")
    val processTimeout: Duration = Duration.ofMinutes(2),
    @field:NotNull
    val finalStatus: ProcessingStatus = ProcessingStatus.COMPLETED,
    @field:NotBlank
    val indexName: String = "paperless-documents",
)
