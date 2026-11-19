package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.persistence.DocumentPersistence
import at.fhtw.swen3.paperless.ocr.config.OcrProcessingProperties
import at.fhtw.swen3.paperless.ocr.model.DocumentDeletion
import at.fhtw.swen3.paperless.ocr.model.OcrJob
import at.fhtw.swen3.paperless.ocr.model.OcrResult
import at.fhtw.swen3.paperless.ocr.model.OcrResultStatus
import at.fhtw.swen3.paperless.ocr.persistence.DocumentIndexPersistence
import at.fhtw.swen3.paperless.ocr.persistence.OcrJobPersistence
import at.fhtw.swen3.paperless.testsupport.any
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class OcrResultServiceImplTest {
    @Mock lateinit var documents: DocumentPersistence
    @Mock lateinit var jobs: OcrJobPersistence
    @Mock lateinit var index: DocumentIndexPersistence
    private val documentId = UUID.randomUUID()
    private val jobId = UUID.randomUUID()
    private val attemptId = UUID.randomUUID()
    private val job = OcrJob(jobId, documentId, "documents/file.pdf", "application/pdf", attemptId, 1)
    private val document = Document(
        documentId, "Edited title", "file.pdf", "application/pdf", 42, ProcessingStatus.PENDING,
        null, "Summary", job.storageKey, null, Instant.now(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), 7,
    )

    private fun service(properties: OcrProcessingProperties = OcrProcessingProperties()) =
        OcrResultServiceImpl(documents, jobs, index, properties)

    @Test
    fun started_updates_only_processing_state_ok() {
        // given
        activeJob()
        // when
        service().apply(result(OcrResultStatus.STARTED))
        // then
        verify(documents).update(documentId, document.copy(status = ProcessingStatus.OCR_IN_PROGRESS))
        verifyNoInteractions(index)
        verify(jobs, never()).delete(jobId)
    }

    @Test
    fun success_preserves_current_metadata_and_finishes_job_ok() {
        // given
        activeJob()
        // when
        service().apply(result(OcrResultStatus.SUCCEEDED, "text"))
        // then
        val order = inOrder(documents, jobs, index)
        order.verify(documents).findLockedById(documentId)
        order.verify(jobs).findLockedById(jobId)
        order.verify(index).index(documentId, "text")
        order.verify(documents).update(documentId, document.copy(status = ProcessingStatus.COMPLETED, ocrContent = "text"))
        order.verify(jobs).delete(jobId)
    }

    @Test
    fun success_can_advance_to_genai_ok() {
        // given
        activeJob()
        // when
        service(OcrProcessingProperties(finalStatus = ProcessingStatus.GENAI_IN_PROGRESS))
            .apply(result(OcrResultStatus.SUCCEEDED, ""))
        // then
        verify(documents).update(documentId, document.copy(status = ProcessingStatus.GENAI_IN_PROGRESS, ocrContent = ""))
    }

    @Test
    fun obsolete_attempt_does_not_update_or_index_ok() {
        // given
        activeJob()
        // when
        service().apply(result(OcrResultStatus.SUCCEEDED, "stale").copy(attemptId = UUID.randomUUID()))
        // then
        verifyNoInteractions(index)
        verify(documents, never()).update(any(UUID::class.java), any(Document::class.java))
        verify(jobs, never()).delete(jobId)
    }

    @Test
    fun mismatched_document_is_rejected_ok() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(document)
        `when`(jobs.findLockedById(jobId)).thenReturn(job.copy(documentId = UUID.randomUUID()))
        // when
        service().apply(result(OcrResultStatus.SUCCEEDED, "text"))
        // then
        verifyNoInteractions(index)
        verify(documents, never()).update(any(UUID::class.java), any(Document::class.java))
    }

    @Test
    fun duplicate_terminal_result_is_ignored_ok() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(document)
        // when
        service().apply(result(OcrResultStatus.SUCCEEDED, "text"))
        // then
        verifyNoInteractions(index)
        verify(documents, never()).update(any(UUID::class.java), any(Document::class.java))
    }

    @Test
    fun deleted_document_is_never_recreated_or_indexed_ok() {
        // when
        service().apply(result(OcrResultStatus.SUCCEEDED, "text"))
        // then
        verifyNoInteractions(jobs, index)
        verify(documents, never()).update(any(UUID::class.java), any(Document::class.java))
    }

    @Test
    fun failure_invalidates_attempt_before_scheduling_retry_ok() {
        // given
        activeJob()
        // when
        service().apply(result(OcrResultStatus.FAILED))
        // then
        verify(documents).update(documentId, document.copy(status = ProcessingStatus.FAILED))
        val captor = ArgumentCaptor.forClass(OcrJob::class.java)
        verify(jobs).update(captor.capture() ?: job)
        assertThat(captor.value.attemptId).isNull()
        assertThat(captor.value.availableAt).isAfter(Instant.now())
        verifyNoInteractions(index)
    }

    @Test
    fun exhausted_failure_stops_retrying_ok() {
        // given
        activeJob()
        // when
        service(OcrProcessingProperties(maxAttempts = 1)).apply(result(OcrResultStatus.FAILED))
        // then
        verify(jobs).delete(jobId)
        verify(jobs, never()).update(any(OcrJob::class.java))
    }

    @Test
    fun index_failure_propagates_without_completing_job_ko() {
        // given
        activeJob()
        val failure = IllegalStateException("index unavailable")
        doThrow(failure).`when`(index).index(documentId, "text")
        // when / then
        assertThatThrownBy { service().apply(result(OcrResultStatus.SUCCEEDED, "text")) }.isSameAs(failure)
        verify(jobs, never()).delete(jobId)
        verify(documents, never()).update(any(UUID::class.java), any(Document::class.java))
    }

    @Test
    fun successful_result_without_text_is_rejected_ko() {
        // given
        activeJob()
        // when / then
        assertThatThrownBy { service().apply(result(OcrResultStatus.SUCCEEDED)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verifyNoInteractions(index)
    }

    @Test
    fun deletion_removes_search_entry_ok() {
        // when
        service().processDeletion(DocumentDeletion(documentId))
        // then
        verify(index).delete(documentId)
    }

    private fun activeJob() {
        `when`(documents.findLockedById(documentId)).thenReturn(document)
        `when`(jobs.findLockedById(jobId)).thenReturn(job)
    }

    private fun result(status: OcrResultStatus, text: String? = null) =
        OcrResult(jobId, documentId, attemptId, status, text)
}
