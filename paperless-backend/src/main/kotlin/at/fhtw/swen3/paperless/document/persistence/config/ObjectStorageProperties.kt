package at.fhtw.swen3.paperless.document.persistence.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties("paperless.storage")
data class ObjectStorageProperties(
    val endpoint: URI = URI.create("http://localhost:9000"),
    val region: String = "us-east-1",
    val bucket: String = "paperless-documents",
    val accessKey: String = "paperless",
    val secretKey: String = "paperless_secure_password"
)
