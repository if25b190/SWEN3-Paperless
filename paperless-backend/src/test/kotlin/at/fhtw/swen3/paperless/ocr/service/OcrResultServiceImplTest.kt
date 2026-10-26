package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.persistence.DocumentPersistence
import at.fhtw.swen3.paperless.ocr.model.OcrResult
import at.fhtw.swen3.paperless.ocr.model.OcrResultStatus
import at.fhtw.swen3.paperless.testsupport.any
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class OcrResultServiceImplTest {
    @Mock
    lateinit var documents: DocumentPersistence

    @InjectMocks
    lateinit var service: OcrResultServiceImpl

    private val documentId = UUID.randomUUID()
    private val result = OcrResult(
        UUID.randomUUID(), documentId, "documents/test.pdf", "application/pdf", OcrResultStatus.COMPLETED,
        text = "Invoice amount 12345"
    )

    @Test
    fun completed_result_updates_text_and_status_without_changing_sharing_ok() {
        // given
        val document = document()
        `when`(documents.findLockedById(documentId)).thenReturn(document)
        `when`(documents.update(any(), any(Document::class.java))).thenAnswer {
            assertThat(it.arguments[0]).isEqualTo(documentId)
            val saved = it.arguments[1] as Document
            assertThat(saved.ocrContent).isEqualTo(result.text)
            assertThat(saved.status).isEqualTo(ProcessingStatus.COMPLETED)
            assertThat(saved.ownerId).isEqualTo(document.ownerId)
            assertThat(saved.teamId).isEqualTo(document.teamId)
            assertThat(saved.title).isEqualTo(document.title)
            assertThat(saved.version).isEqualTo(document.version)
            saved
        }

        // when
        service.apply(result)

        // then
        verify(documents).update(any(), any(Document::class.java))
        verify(documents, never()).save(any(Document::class.java))
    }

    @Test
    fun deleted_document_is_not_recreated_by_a_late_result_ok() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(null)

        // when
        service.apply(result)

        // then
        verify(documents, never()).update(any(), any(Document::class.java))
    }

    @Test
    fun mismatched_object_key_does_not_update_another_document_ko() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(document())

        // when
        service.apply(result.copy(storageKey = "documents/different.pdf"))

        // then
        verify(documents, never()).update(any(), any(Document::class.java))
    }

    @Test
    fun duplicate_success_is_idempotent_ok() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(
            document().copy(status = ProcessingStatus.COMPLETED, ocrContent = result.text)
        )

        // when
        service.apply(result)

        // then
        verify(documents, never()).update(any(), any(Document::class.java))
    }

    @Test
    fun late_failure_does_not_downgrade_a_completed_document_ok() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(
            document().copy(status = ProcessingStatus.COMPLETED, ocrContent = result.text)
        )

        // when
        service.apply(result.copy(status = OcrResultStatus.FAILED, text = null))

        // then
        verify(documents, never()).update(any(), any(Document::class.java))
    }

    @Test
    fun failed_result_marks_pending_document_failed_ok() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(document())
        `when`(documents.update(any(), any(Document::class.java))).thenAnswer {
            assertThat(it.arguments[0]).isEqualTo(documentId)
            val saved = it.arguments[1] as Document
            assertThat(saved.status).isEqualTo(ProcessingStatus.FAILED)
            saved
        }

        // when
        service.apply(result.copy(status = OcrResultStatus.FAILED, text = null))

        // then
        verify(documents).update(any(), any(Document::class.java))
        verify(documents, never()).save(any(Document::class.java))
    }

    @Test
    fun completed_result_without_text_is_rejected_before_persistence_ko() {
        // then
        assertThatThrownBy { service.apply(result.copy(text = null)) }.isInstanceOf(IllegalArgumentException::class.java)
        verifyNoInteractions(documents)
    }

    private fun document(): Document = Document(
        id = documentId, title = "Private invoice", originalFilename = "test.pdf", contentType = "application/pdf",
        fileSize = 10, status = ProcessingStatus.PENDING, ocrContent = null, summary = null,
        storageKey = result.storageKey, documentType = null, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        ownerId = UUID.randomUUID(), teamId = UUID.randomUUID(), version = 3
    )
}
