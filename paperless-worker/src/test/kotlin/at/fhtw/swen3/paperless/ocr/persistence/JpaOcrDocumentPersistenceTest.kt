package at.fhtw.swen3.paperless.ocr.persistence

import at.fhtw.swen3.paperless.ocr.model.ProcessingStatus
import at.fhtw.swen3.paperless.ocr.model.WorkerDocument
import at.fhtw.swen3.paperless.ocr.persistence.mapper.WorkerDocumentEntityMapper
import at.fhtw.swen3.paperless.ocr.persistence.mapper.WorkerDocumentEntityMapperImpl
import jakarta.persistence.EntityManager
import jakarta.persistence.OptimisticLockException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.util.UUID

@DataJpaTest
@Import(JpaOcrDocumentPersistence::class, WorkerDocumentEntityMapperImpl::class)
class JpaOcrDocumentPersistenceTest {
    @Autowired
    lateinit var persistence: OcrDocumentPersistence

    @Autowired
    lateinit var entityManager: EntityManager

    @Autowired
    lateinit var mapper: WorkerDocumentEntityMapper

    @Test
    fun locked_lookup_preserves_domain_fields_ok() {
        // given
        val document = saveDocument()

        // when
        val loaded = persistence.findLockedById(document.id)

        // then
        assertThat(loaded).isEqualTo(document)
    }

    @Test
    fun unknown_document_returns_null_ok() {
        // when
        val loaded = persistence.findLockedById(UUID.randomUUID())

        // then
        assertThat(loaded).isNull()
    }

    @Test
    fun update_maps_content_status_and_version_ok() {
        // given
        val document = saveDocument()
        val update = document.copy(status = ProcessingStatus.COMPLETED, ocrContent = "INVOICE #9821")

        // when
        val saved = persistence.update(update)
        entityManager.clear()

        // then
        assertThat(saved).isEqualTo(update.copy(version = 1))
        assertThat(persistence.findLockedById(document.id)).isEqualTo(saved)
    }

    @Test
    fun stale_version_is_rejected_ko() {
        // given
        val document = saveDocument()
        persistence.update(document.copy(status = ProcessingStatus.OCR_IN_PROGRESS))

        // when / then
        assertThatThrownBy { persistence.update(document.copy(status = ProcessingStatus.COMPLETED)) }
            .isInstanceOf(OptimisticLockException::class.java)
    }

    @Test
    fun deleted_document_is_rejected_ko() {
        // given
        val document = WorkerDocument(UUID.randomUUID(), ProcessingStatus.PENDING, null, 0)

        // when / then
        assertThatThrownBy { persistence.update(document) }.isInstanceOf(OptimisticLockException::class.java)
    }

    private fun saveDocument(): WorkerDocument {
        val document = WorkerDocument(UUID.randomUUID(), ProcessingStatus.PENDING, null, 0)
        entityManager.persist(mapper.toEntity(document))
        entityManager.flush()
        entityManager.clear()

        return document
    }
}
