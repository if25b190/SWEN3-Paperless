package at.fhtw.swen3.paperless.ocr

import at.fhtw.swen3.paperless.document.model.DocumentUpload
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.persistence.repository.DocumentRepository
import at.fhtw.swen3.paperless.document.service.DocumentService
import at.fhtw.swen3.paperless.ocr.messaging.OcrMessagePublisher
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.persistence.repository.OcrJobRepository
import at.fhtw.swen3.paperless.ocr.service.OcrDispatchService
import at.fhtw.swen3.paperless.testsupport.any
import at.fhtw.swen3.paperless.user.entity.UserEntity
import at.fhtw.swen3.paperless.user.persistence.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Path

@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:ocr-outbox;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"])
class OcrOutboxIntegrationTest(
    @Autowired private val documents: DocumentService,
    @Autowired private val documentRepository: DocumentRepository,
    @Autowired private val jobs: OcrJobRepository,
    @Autowired private val users: UserRepository,
    @Autowired private val dispatch: OcrDispatchService,
    @Autowired private val transactionManager: PlatformTransactionManager
) {
    @MockitoBean
    lateinit var publisher: OcrMessagePublisher

    @BeforeEach
    fun create_actor() {
        val owner = users.saveAndFlush(UserEntity(username = "ocr-outbox-owner", password = "hash"))
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(owner.id.toString(), "test")
    }

    @AfterEach
    fun clean_up() {
        jobs.deleteAll()
        documentRepository.findAll().forEach { documents.deleteDocument(requireNotNull(it.id)) }
        users.deleteAll()
        SecurityContextHolder.clearContext()
    }

    @Test
    fun upload_commits_document_and_job_without_contacting_broker_ok() {
        // when
        val document = documents.uploadDocument(upload())

        // then
        val job = jobs.findAll().single()
        assertThat(job.documentId).isEqualTo(document.id)
        assertThat(job.storageKey).isEqualTo(document.storageKey)
        assertThat(job.contentType).isEqualTo("application/pdf")
        assertThat(documentRepository.existsById(requireNotNull(document.id))).isTrue()
        verifyNoInteractions(publisher)
    }

    @Test
    fun broker_failure_retains_job_then_retries_same_id_after_recovery_ok() {
        // given
        val document = documents.uploadDocument(upload())
        val jobId = requireNotNull(jobs.findAll().single().id)
        doThrow(IllegalStateException("broker failure", IllegalStateException("broker unavailable")))
            .`when`(publisher).publish(any(OcrRequest::class.java))

        // when
        assertThatThrownBy { dispatch.dispatchNext() }.isInstanceOf(IllegalStateException::class.java)

        // then
        assertThat(jobs.existsById(jobId)).isTrue()
        doNothing().`when`(publisher).publish(any(OcrRequest::class.java))
        dispatch.dispatchNext()
        assertThat(jobs.count()).isZero()
        assertThat(documents.getDocument(requireNotNull(document.id)).status).isEqualTo(ProcessingStatus.PENDING)
        verify(publisher, times(2)).publish(
            OcrRequest(jobId, requireNotNull(document.id), requireNotNull(document.storageKey), document.contentType)
        )
    }

    @Test
    fun rollback_removes_document_job_and_file_ok() {
        // when
        val rolledBack = requireNotNull(TransactionTemplate(transactionManager).execute { transaction ->
            val document = documents.uploadDocument(upload())
            transaction.setRollbackOnly()
            document
        })

        // then
        assertThat(documentRepository.count()).isZero()
        assertThat(jobs.count()).isZero()
        val filename = requireNotNull(rolledBack.storageKey).substringAfterLast('/')
        assertThat(Path.of(System.getProperty("java.io.tmpdir"), "paperless-documents", filename)).doesNotExist()
        verifyNoInteractions(publisher)
    }

    private fun upload() = DocumentUpload("OCR test", "test.pdf", "application/pdf", byteArrayOf(1, 2), null, null)
}
