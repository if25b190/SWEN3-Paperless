package at.fhtw.swen3.paperless.ocr.config

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.net.URI

@Validated
@ConfigurationProperties("paperless.storage")
data class ObjectStorageProperties(
    @field:NotNull
    val endpoint: URI = URI.create("http://localhost:9000"),
    @field:NotBlank
    val region: String = "us-east-1",
    @field:NotBlank
    val bucket: String = "paperless-documents",
    @field:NotBlank
    val accessKey: String = "paperless",
    @field:NotBlank
    val secretKey: String = "paperless_secure_password",
)
