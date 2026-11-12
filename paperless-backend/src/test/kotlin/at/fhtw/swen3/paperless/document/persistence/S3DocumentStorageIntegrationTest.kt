package at.fhtw.swen3.paperless.document.persistence

import at.fhtw.swen3.paperless.document.persistence.config.ObjectStorageConfiguration
import at.fhtw.swen3.paperless.document.persistence.config.ObjectStorageProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.net.URI
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "PAPERLESS_S3_TEST", matches = "true")
class S3DocumentStorageIntegrationTest {
    @Test
    fun rustfs_roundtrip_and_delete_preserve_original_bytes_ok() {
        // given
        val properties = ObjectStorageProperties(
            endpoint = URI.create(System.getenv("S3_ENDPOINT") ?: "http://localhost:9000"),
            bucket = System.getenv("S3_BUCKET") ?: "paperless-documents",
            accessKey = System.getenv("RUSTFS_ACCESS_KEY") ?: "paperless",
            secretKey = System.getenv("RUSTFS_SECRET_KEY") ?: "paperless_secure_password"
        )
        val key = "documents/storage-test-${UUID.randomUUID()}.pdf"
        val content = "%PDF-1.4\nstorage test".toByteArray()

        ObjectStorageConfiguration().documentS3Client(properties).use { client ->
            val storage = S3DocumentStorage(client, properties)
            try {
                // when
                storage.store(key, content, "application/pdf")

                // then
                assertThat(storage.find(key)).isEqualTo(content)
                storage.delete(key)
                assertThat(storage.find(key)).isNull()
            } finally {
                storage.delete(key)
            }
        }
    }
}
