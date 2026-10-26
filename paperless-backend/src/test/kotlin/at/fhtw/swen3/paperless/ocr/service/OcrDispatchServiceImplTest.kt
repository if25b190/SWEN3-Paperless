package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.ocr.messaging.OcrMessagePublisher
import at.fhtw.swen3.paperless.ocr.model.OcrJob
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.persistence.OcrJobPersistence
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class OcrDispatchServiceImplTest {
    @Mock
    lateinit var jobs: OcrJobPersistence

    @Mock
    lateinit var publisher: OcrMessagePublisher

    @InjectMocks
    lateinit var service: OcrDispatchServiceImpl

    private val job = OcrJob(UUID.randomUUID(), UUID.randomUUID(), "documents/test.pdf", "application/pdf")
    private val request = OcrRequest(requireNotNull(job.id), job.documentId, job.storageKey, job.contentType)

    @Test
    fun confirmed_publish_removes_pending_job_ok() {
        // given
        `when`(jobs.findNextForDispatch()).thenReturn(job)

        // when
        service.dispatchNext()

        // then
        val ordered = inOrder(publisher, jobs)
        ordered.verify(publisher).publish(request)
        ordered.verify(jobs).delete(requireNotNull(job.id))
    }

    @Test
    fun empty_outbox_does_not_publish_ok() {
        // when
        service.dispatchNext()

        // then
        verifyNoInteractions(publisher)
    }

    @Test
    fun publish_failure_retains_pending_job_for_retry_ko() {
        // given
        `when`(jobs.findNextForDispatch()).thenReturn(job)
        val failure = IllegalStateException("broker failure", IllegalStateException("broker unavailable"))
        doThrow(failure).`when`(publisher).publish(request)

        // then
        assertThatThrownBy { service.dispatchNext() }.isInstanceOf(IllegalStateException::class.java).isSameAs(failure)
        verify(jobs, never()).delete(request.jobId)
    }

    @Test
    fun persistence_failure_is_translated_to_service_exception_ko() {
        // given
        val failure = IllegalStateException("database failure", IllegalStateException("database unavailable"))
        `when`(jobs.findNextForDispatch()).thenThrow(failure)

        // then
        assertThatThrownBy { service.dispatchNext() }.isInstanceOf(IllegalStateException::class.java).isSameAs(failure)
        verifyNoInteractions(publisher)
    }
}
