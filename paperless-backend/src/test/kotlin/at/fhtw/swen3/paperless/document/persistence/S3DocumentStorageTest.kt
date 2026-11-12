package at.fhtw.swen3.paperless.document.persistence

import at.fhtw.swen3.paperless.document.persistence.config.ObjectStorageProperties
import at.fhtw.swen3.paperless.document.persistence.exception.DocumentStoragePersistenceException
import at.fhtw.swen3.paperless.testsupport.any
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

@ExtendWith(MockitoExtension::class)
class S3DocumentStorageTest {
    @Mock
    lateinit var client: S3Client

    private val properties = ObjectStorageProperties(bucket = "test-documents")
    private val key = "documents/original.pdf"

    @Test
    fun store_preserves_full_object_key_and_content_type_ok() {
        // given
        val content = byteArrayOf(1, 2, 3)

        // when
        S3DocumentStorage(client, properties).store(key, content, "application/pdf")

        // then
        verify(client).putObject(
            org.mockito.ArgumentMatchers.eq(
                PutObjectRequest.builder().bucket(properties.bucket).key(key).contentType("application/pdf").build()
            ),
            any(RequestBody::class.java)
        )
    }

    @Test
    fun find_returns_original_object_bytes_ok() {
        // given
        val content = byteArrayOf(4, 5, 6)
        `when`(client.getObjectAsBytes(any(GetObjectRequest::class.java)))
            .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content))

        // when
        val stored = S3DocumentStorage(client, properties).find(key)

        // then
        assertThat(stored).isEqualTo(content)
        verify(client).getObjectAsBytes(GetObjectRequest.builder().bucket(properties.bucket).key(key).build())
    }

    @Test
    fun missing_object_returns_empty_result_ok() {
        // given
        `when`(client.getObjectAsBytes(any(GetObjectRequest::class.java)))
            .thenThrow(S3Exception.builder().statusCode(404).build())

        // then
        assertThat(S3DocumentStorage(client, properties).find(key)).isNull()
    }

    @Test
    fun storage_outage_is_not_reported_as_missing_document_ko() {
        // given
        val failure = S3Exception.builder().statusCode(503).message("private endpoint information").build()
        `when`(client.getObjectAsBytes(any(GetObjectRequest::class.java))).thenThrow(failure)

        // then
        assertThatThrownBy { S3DocumentStorage(client, properties).find(key) }
            .isInstanceOf(DocumentStoragePersistenceException::class.java).hasCause(failure)
    }

    @Test
    fun delete_uses_the_same_bucket_and_object_key_ok() {
        // when
        S3DocumentStorage(client, properties).delete(key)

        // then
        verify(client).deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket).key(key).build())
    }
}
