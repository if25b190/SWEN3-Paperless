package at.fhtw.swen3.paperless.document.persistence

import at.fhtw.swen3.paperless.document.persistence.config.ObjectStorageProperties
import at.fhtw.swen3.paperless.document.persistence.exception.DocumentStoragePersistenceException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

@Repository
@ConditionalOnProperty(prefix = "paperless.storage", name = ["type"], havingValue = "s3", matchIfMissing = true)
class S3DocumentStorage(
    private val client: S3Client,
    private val properties: ObjectStorageProperties
) : DocumentStoragePersistence {
    override fun store(storageKey: String, content: ByteArray, contentType: String) {
        translateFailure {
            val request = PutObjectRequest.builder().bucket(properties.bucket).key(storageKey)
                .contentType(contentType).build()
            client.putObject(request, RequestBody.fromBytes(content))
        }
    }

    override fun find(storageKey: String): ByteArray? = translateFailure {
        try {
            val request = GetObjectRequest.builder().bucket(properties.bucket).key(storageKey).build()

            client.getObjectAsBytes(request).asByteArray()
        } catch (exception: S3Exception) {
            if (exception.statusCode() != 404) throw exception
            null
        }
    }

    override fun delete(storageKey: String) {
        translateFailure {
            client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket).key(storageKey).build())
        }
    }

    private fun <T> translateFailure(action: () -> T): T = try {
        action()
    } catch (exception: SdkException) {
        throw DocumentStoragePersistenceException(exception)
    }
}
