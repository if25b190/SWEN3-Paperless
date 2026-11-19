package at.fhtw.swen3.paperless.ocr

import at.fhtw.swen3.paperless.document.model.DocumentUpdate
import at.fhtw.swen3.paperless.document.model.DocumentUpload
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.persistence.repository.DocumentRepository
import at.fhtw.swen3.paperless.document.service.DocumentService
import at.fhtw.swen3.paperless.ocr.messaging.OcrMessagePublisher
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.model.OcrResult
import at.fhtw.swen3.paperless.ocr.model.OcrResultStatus
import at.fhtw.swen3.paperless.ocr.persistence.DocumentIndexPersistence
import at.fhtw.swen3.paperless.ocr.persistence.repository.OcrJobRepository
import at.fhtw.swen3.paperless.ocr.service.OcrDispatchService
import at.fhtw.swen3.paperless.ocr.service.OcrResultService
import at.fhtw.swen3.paperless.testsupport.any
import at.fhtw.swen3.paperless.user.entity.UserEntity
import at.fhtw.swen3.paperless.user.persistence.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:ocr-processing;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"])
class OcrProcessingIntegrationTest {
    @Autowired lateinit var documents: DocumentService
    @Autowired lateinit var documentRepository: DocumentRepository
    @Autowired lateinit var jobs: OcrJobRepository
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var dispatch: OcrDispatchService
    @Autowired lateinit var results: OcrResultService
    @MockitoBean lateinit var publisher: OcrMessagePublisher
    @MockitoBean lateinit var index: DocumentIndexPersistence
    private lateinit var ownerId: UUID

    @BeforeEach
    fun createActor() {
        ownerId = requireNotNull(users.saveAndFlush(UserEntity(username = "processing-owner", password = "hash")).id)
    }

    @AfterEach
    fun cleanUp() {
        jobs.deleteAll()
        documentRepository.findAll().forEach { documents.deleteDocument(ownerId, requireNotNull(it.id)) }
        users.deleteAll()
    }

    @Test
    fun progress_is_committed_and_metadata_can_change_while_worker_runs_ok() {
        // given
        val request = dispatchUpload()
        results.apply(result(request, OcrResultStatus.STARTED))
        // when
        val edited = documents.updateDocument(ownerId, request.documentId, DocumentUpdate(null, false, "User edit", null))
        results.apply(result(request, OcrResultStatus.SUCCEEDED, "OCR text"))
        // then
        assertThat(edited.status).isEqualTo(ProcessingStatus.OCR_IN_PROGRESS)
        val document = documents.getDocument(ownerId, request.documentId)
        assertThat(document.title).isEqualTo("User edit")
        assertThat(document.ocrContent).isEqualTo("OCR text")
        assertThat(document.status).isEqualTo(ProcessingStatus.COMPLETED)
        assertThat(jobs.count()).isZero()
        verify(index).index(request.documentId, "OCR text")
    }

    @Test
    fun simultaneous_metadata_update_and_result_preserve_both_changes_ok() {
        // given
        val request = dispatchUpload()
        results.apply(result(request, OcrResultStatus.STARTED))
        // when
        val edit = CompletableFuture.runAsync {
            documents.updateDocument(ownerId, request.documentId, DocumentUpdate(null, false, "Concurrent edit", null))
        }
        val complete = CompletableFuture.runAsync { results.apply(result(request, OcrResultStatus.SUCCEEDED, "text")) }
        CompletableFuture.allOf(edit, complete).get(10, TimeUnit.SECONDS)
        // then
        val document = documents.getDocument(ownerId, request.documentId)
        assertThat(document.title).isEqualTo("Concurrent edit")
        assertThat(document.ocrContent).isEqualTo("text")
        assertThat(document.status).isEqualTo(ProcessingStatus.COMPLETED)
    }

    @Test
    fun duplicate_success_and_delayed_progress_do_not_change_completed_document_ok() {
        // given
        val request = dispatchUpload()
        results.apply(result(request, OcrResultStatus.SUCCEEDED, "first"))
        val version = documents.getDocument(ownerId, request.documentId).version
        // when
        results.apply(result(request, OcrResultStatus.SUCCEEDED, "duplicate"))
        results.apply(result(request, OcrResultStatus.STARTED))
        results.apply(result(request, OcrResultStatus.FAILED))
        // then
        val document = documents.getDocument(ownerId, request.documentId)
        assertThat(document.ocrContent).isEqualTo("first")
        assertThat(document.status).isEqualTo(ProcessingStatus.COMPLETED)
        assertThat(document.version).isEqualTo(version)
        verify(index).index(request.documentId, "first")
    }

    @Test
    fun document_deleted_during_ocr_is_not_recreated_or_indexed_ok() {
        // given
        val request = dispatchUpload()
        results.apply(result(request, OcrResultStatus.STARTED))
        documents.deleteDocument(ownerId, request.documentId)
        // when
        results.apply(result(request, OcrResultStatus.SUCCEEDED, "late"))
        // then
        assertThat(documentRepository.existsById(request.documentId)).isFalse()
        verifyNoInteractions(index)
    }

    @Test
    fun expired_lease_creates_new_attempt_and_fences_old_result_ok() {
        // given
        val old = dispatchUpload()
        makeReady(old.jobId)
        // when
        dispatch.dispatchNext()
        val current = latestRequest(2)
        results.apply(result(old, OcrResultStatus.SUCCEEDED, "stale"))
        // then
        assertThat(current.attemptId).isNotEqualTo(old.attemptId)
        assertThat(jobs.findById(old.jobId).orElseThrow().attempts).isEqualTo(2)
        verifyNoInteractions(index)
        // when
        results.apply(result(current, OcrResultStatus.SUCCEEDED, "current"))
        // then
        assertThat(documents.getDocument(ownerId, current.documentId).ocrContent).isEqualTo("current")
    }

    @Test
    fun failure_invalidates_attempt_and_retries_with_new_token_ok() {
        // given
        val old = dispatchUpload()
        // when
        results.apply(result(old, OcrResultStatus.FAILED))
        results.apply(result(old, OcrResultStatus.SUCCEEDED, "obsolete"))
        // then
        assertThat(documents.getDocument(ownerId, old.documentId).status).isEqualTo(ProcessingStatus.FAILED)
        assertThat(jobs.findById(old.jobId).orElseThrow().attemptId).isNull()
        verifyNoInteractions(index)
        // when
        makeReady(old.jobId)
        dispatch.dispatchNext()
        results.apply(result(latestRequest(2), OcrResultStatus.SUCCEEDED, "recovered"))
        // then
        assertThat(documents.getDocument(ownerId, old.documentId).ocrContent).isEqualTo("recovered")
        assertThat(jobs.count()).isZero()
    }

    @Test
    fun expired_final_attempt_is_marked_failed_and_removed_ok() {
        // given
        val request = dispatchUpload()
        val job = jobs.findById(request.jobId).orElseThrow()
        job.attempts = 4
        job.availableAt = Instant.now().minusSeconds(1)
        jobs.saveAndFlush(job)
        // when
        dispatch.dispatchNext()
        // then
        assertThat(documents.getDocument(ownerId, request.documentId).status).isEqualTo(ProcessingStatus.FAILED)
        assertThat(jobs.count()).isZero()
        verify(publisher).publish(any(OcrRequest::class.java))
    }

    @Test
    fun index_failure_rolls_back_result_and_redelivery_completes_ok() {
        // given
        val request = dispatchUpload()
        val success = result(request, OcrResultStatus.SUCCEEDED, "text")
        doThrow(IllegalStateException("index unavailable")).`when`(index).index(request.documentId, "text")
        // when / then
        assertThatThrownBy { results.apply(success) }.isInstanceOf(IllegalStateException::class.java)
        assertThat(documents.getDocument(ownerId, request.documentId).status).isEqualTo(ProcessingStatus.PENDING)
        assertThat(jobs.existsById(request.jobId)).isTrue()
        // when
        doNothing().`when`(index).index(request.documentId, "text")
        results.apply(success)
        // then
        assertThat(documents.getDocument(ownerId, request.documentId).status).isEqualTo(ProcessingStatus.COMPLETED)
        assertThat(jobs.count()).isZero()
    }

    @Test
    fun two_dispatchers_cannot_publish_two_active_attempts_ok() {
        // given
        documents.uploadDocument(ownerId, upload())
        // when
        val first = CompletableFuture.runAsync { dispatch.dispatchNext() }
        val second = CompletableFuture.runAsync { dispatch.dispatchNext() }
        CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS)
        // then
        verify(publisher).publish(any(OcrRequest::class.java))
        assertThat(jobs.findAll().single().attempts).isEqualTo(1)
    }

    private fun dispatchUpload(): OcrRequest {
        documents.uploadDocument(ownerId, upload())
        dispatch.dispatchNext()

        return latestRequest(1)
    }

    private fun latestRequest(count: Int): OcrRequest {
        val captor = ArgumentCaptor.forClass(OcrRequest::class.java)
        verify(publisher, times(count)).publish(captor.capture() ?: OcrRequest(UUID.randomUUID(), UUID.randomUUID(), "", "", UUID.randomUUID()))

        return captor.allValues.last()
    }

    private fun makeReady(id: UUID) {
        val job = jobs.findById(id).orElseThrow()
        job.availableAt = Instant.now().minusSeconds(1)
        jobs.saveAndFlush(job)
    }

    private fun upload() = DocumentUpload("OCR test", "test.pdf", "application/pdf", byteArrayOf(1, 2), null, null)
    private fun result(request: OcrRequest, status: OcrResultStatus, text: String? = null) =
        OcrResult(request.jobId, request.documentId, request.attemptId, status, text)
}
