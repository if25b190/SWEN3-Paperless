package at.fhtw.swen3.paperless.ocr.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("paperless.ocr")
data class OcrMessagingProperties(
    val exchange: String = "paperless.ocr",
    val queue: String = "paperless.ocr.requests",
    val routingKey: String = "document.uploaded",
    val deadLetterExchange: String = "paperless.ocr.dead-letter",
    val deadLetterQueue: String = "paperless.ocr.failed",
    val confirmTimeout: Duration = Duration.ofSeconds(5)
)
