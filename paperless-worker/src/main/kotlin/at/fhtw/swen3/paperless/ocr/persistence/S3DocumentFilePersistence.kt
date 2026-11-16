package at.fhtw.swen3.paperless.ocr.persistence

import at.fhtw.swen3.paperless.ocr.config.ObjectStorageProperties
import at.fhtw.swen3.paperless.ocr.persistence.exception.DocumentFilePersistenceException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

@Repository
@ConditionalOnProperty(prefix = "paperless.storage", name = ["type"], havingValue = "s3", matchIfMissing = true)
class S3DocumentFilePersistence(private val client: S3Client, private val properties: ObjectStorageProperties) :
    DocumentFilePersistence {
    override fun find(storageKey: String): ByteArray? = translateFailure {
        try {
            val request = GetObjectRequest.builder().bucket(properties.bucket).key(storageKey).build()

            client.getObjectAsBytes(request).asByteArray()
        } catch (exception: S3Exception) {
            if (exception.statusCode() != 404) throw exception
            null
        }
    }

    private fun <T> translateFailure(action: () -> T): T = try {
        action()
    } catch (exception: SdkException) {
        throw DocumentFilePersistenceException(exception)
    }
}
