package at.fhtw.swen3.paperless.ocr.persistence

import at.fhtw.swen3.paperless.ocr.entity.WorkerDocumentEntity
import at.fhtw.swen3.paperless.ocr.model.ProcessingStatus
import at.fhtw.swen3.paperless.ocr.model.WorkerDocument
import at.fhtw.swen3.paperless.ocr.persistence.mapper.WorkerDocumentEntityMapper
import at.fhtw.swen3.paperless.ocr.persistence.mapper.WorkerDocumentEntityMapperImpl
import at.fhtw.swen3.paperless.ocr.persistence.repository.OcrDocumentRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.util.UUID

@DataJpaTest
@Import(JpaOcrDocumentPersistence::class, WorkerDocumentEntityMapperImpl::class)
class JpaOcrDocumentPersistenceTest(
    @Autowired private val repository: OcrDocumentRepository,
    @Autowired private val entityManager: EntityManager,
    @Autowired private val persistence: OcrDocumentPersistence,
    @Autowired private val mapper: WorkerDocumentEntityMapper
) {
    private var documentId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        documentId = UUID.randomUUID()
    }

    @Test
    fun claim_pending_document_ok() {
        // given
        saveDocument(ProcessingStatus.PENDING)

        // when
        val claimed = persistence.claim(documentId)

        // then
        assertThat(claimed).isNotNull()
        assertThat(claimed!!.id).isEqualTo(documentId)
        assertThat(claimed.status).isEqualTo(ProcessingStatus.OCR_IN_PROGRESS)
        assertThat(claimed.ocrContent).isNull()
        assertThat(claimed.version).isEqualTo(1)
        val loaded = loadDocument()
        assertThat(loaded.status).isEqualTo(ProcessingStatus.OCR_IN_PROGRESS)
        assertThat(loaded.version).isEqualTo(1)
    }

    @Test
    fun claim_failed_document_allows_retry_ok() {
        // given
        saveDocument(ProcessingStatus.FAILED)

        // when
        val claimed = persistence.claim(documentId)

        // then
        assertThat(claimed).isNotNull()
        assertThat(claimed!!.status).isEqualTo(ProcessingStatus.OCR_IN_PROGRESS)
        assertThat(loadDocument().status).isEqualTo(ProcessingStatus.OCR_IN_PROGRESS)
    }

    @Test
    fun claim_in_progress_document_returns_false_ok() {
        // given
        saveDocument(ProcessingStatus.OCR_IN_PROGRESS)

        // when
        val claimed = persistence.claim(documentId)

        // then
        assertThat(claimed).isNull()
        assertThat(loadDocument().status).isEqualTo(ProcessingStatus.OCR_IN_PROGRESS)
    }

    @Test
    fun claim_completed_document_returns_false_ok() {
        // given
        saveDocument(ProcessingStatus.COMPLETED)

        // when
        val claimed = persistence.claim(documentId)

        // then
        assertThat(claimed).isNull()
        assertThat(loadDocument().status).isEqualTo(ProcessingStatus.COMPLETED)
    }

    @Test
    fun claim_unknown_document_returns_false_ok() {
        // when
        val claimed = persistence.claim(UUID.randomUUID())

        // then
        assertThat(claimed).isNull()
    }

    @Test
    fun complete_stores_text_and_terminal_status_ok() {
        // given
        saveDocument(ProcessingStatus.OCR_IN_PROGRESS)

        // when
        persistence.complete(documentId, "INVOICE #9821", ProcessingStatus.COMPLETED)

        // then
        val loaded = loadDocument()
        assertThat(loaded.status).isEqualTo(ProcessingStatus.COMPLETED)
        assertThat(loaded.ocrContent).isEqualTo("INVOICE #9821")
        assertThat(loaded.version).isEqualTo(1)
    }

    @Test
    fun complete_ignores_document_not_in_progress_ok() {
        // given
        saveDocument(ProcessingStatus.PENDING)

        // when
        persistence.complete(documentId, "text", ProcessingStatus.COMPLETED)

        // then
        val loaded = loadDocument()
        assertThat(loaded.status).isEqualTo(ProcessingStatus.PENDING)
        assertThat(loaded.ocrContent).isNull()
    }

    @Test
    fun fail_marks_document_failed_ok() {
        // given
        saveDocument(ProcessingStatus.OCR_IN_PROGRESS)

        // when
        persistence.fail(documentId)

        // then
        val loaded = loadDocument()
        assertThat(loaded.status).isEqualTo(ProcessingStatus.FAILED)
        assertThat(loaded.ocrContent).isNull()
    }

    private fun saveDocument(status: ProcessingStatus) {
        entityManager.persist(mapper.toEntity(WorkerDocument(documentId, status, null, 0)))
        entityManager.flush()
    }

    private fun loadDocument(): WorkerDocumentEntity {
        // Evict the cached entity so the read returns the transitioned row
        // from the database.
        entityManager.clear()
        return requireNotNull(repository.findById(documentId).orElse(null))
    }
}
