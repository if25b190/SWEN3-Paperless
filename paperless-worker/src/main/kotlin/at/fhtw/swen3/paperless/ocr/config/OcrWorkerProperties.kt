package at.fhtw.swen3.paperless.ocr.config

import at.fhtw.swen3.paperless.ocr.model.ProcessingStatus
import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

@ConfigurationProperties("paperless.worker")
data class OcrWorkerProperties(
    val enabled: Boolean = true,
    val storageDir: Path = Paths.get("/tmp/paperless-documents"),
    val tesseractLanguages: String = "eng",
    val dpi: Int = 300,
    val processTimeout: Duration = Duration.ofMinutes(2),
    val finalStatus: ProcessingStatus = ProcessingStatus.COMPLETED
)
