package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.persistence.DocumentPersistence
import at.fhtw.swen3.paperless.exception.OcrJobInvalidException
import at.fhtw.swen3.paperless.exception.OcrPublishException
import at.fhtw.swen3.paperless.ocr.config.OcrProcessingProperties
import at.fhtw.swen3.paperless.ocr.messaging.OcrMessagePublisher
import at.fhtw.swen3.paperless.ocr.model.OcrJob
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.persistence.OcrJobPersistence
import at.fhtw.swen3.paperless.testsupport.any
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
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
class OcrDispatchServiceImplTest {
    @Mock lateinit var jobs: OcrJobPersistence
    @Mock lateinit var publisher: OcrMessagePublisher
    @Mock lateinit var documents: DocumentPersistence
    @Mock lateinit var document: Document
    private val job = OcrJob(UUID.randomUUID(), UUID.randomUUID(), "documents/test.pdf", "application/pdf")
    private lateinit var service: OcrDispatchServiceImpl

    @BeforeEach
    fun setUp() {
        service = OcrDispatchServiceImpl(jobs, publisher, documents, OcrProcessingProperties())
    }

    @Test
    fun confirmed_publish_retains_job_with_new_attempt_and_lease_ok() {
        // given
        readyJob()
        // when
        service.dispatchNext()
        // then
        val request = ArgumentCaptor.forClass(OcrRequest::class.java)
        val updated = ArgumentCaptor.forClass(OcrJob::class.java)
        val order = inOrder(documents, jobs, publisher)
        order.verify(documents).findLockedById(job.documentId)
        order.verify(jobs).findLockedById(requireNotNull(job.id))
        order.verify(jobs).update(updated.capture() ?: job)
        order.verify(publisher).publish(request.capture() ?: OcrRequest(requireNotNull(job.id), job.documentId, job.storageKey, job.contentType, UUID.randomUUID()))
        assertThat(updated.value.attemptId).isEqualTo(request.value.attemptId)
        assertThat(updated.value.attempts).isEqualTo(1)
        assertThat(updated.value.availableAt).isAfter(Instant.now())
        verify(jobs, never()).delete(requireNotNull(job.id))
    }

    @Test
    fun empty_outbox_does_not_publish_ok() {
        // when
        service.dispatchNext()
        // then
        verifyNoInteractions(publisher, documents)
    }

    @Test
    fun publish_failure_propagates_for_transaction_rollback_ko() {
        // given
        readyJob()
        val failure = OcrPublishException()
        doThrow(failure).`when`(publisher).publish(any(OcrRequest::class.java))
        // when / then
        assertThatThrownBy { service.dispatchNext() }.isSameAs(failure)
        verify(jobs, never()).delete(requireNotNull(job.id))
    }

    @Test
    fun job_without_id_is_rejected_before_publish_ko() {
        // given
        `when`(jobs.findNextForDispatch()).thenReturn(job.copy(id = null))
        // when / then
        assertThatThrownBy { service.dispatchNext() }.isInstanceOf(OcrJobInvalidException::class.java)
        verifyNoInteractions(publisher, documents)
    }

    @Test
    fun candidate_dispatched_by_another_backend_is_skipped_ok() {
        // given
        readyJob(job.copy(availableAt = Instant.now().plusSeconds(60)))
        // when
        service.dispatchNext()
        // then
        verifyNoInteractions(publisher)
        verify(jobs, never()).update(any(OcrJob::class.java))
    }

    @Test
    fun deleted_document_is_skipped_ok() {
        // given
        `when`(jobs.findNextForDispatch()).thenReturn(job)
        // when
        service.dispatchNext()
        // then
        verifyNoInteractions(publisher)
        verify(jobs, never()).update(any(OcrJob::class.java))
    }

    private fun readyJob(locked: OcrJob = job) {
        `when`(jobs.findNextForDispatch()).thenReturn(job)
        `when`(documents.findLockedById(job.documentId)).thenReturn(document)
        `when`(jobs.findLockedById(requireNotNull(job.id))).thenReturn(locked)
    }
}
