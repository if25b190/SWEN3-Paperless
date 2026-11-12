package at.fhtw.swen3.paperless.ocr.persistence

import at.fhtw.swen3.paperless.ocr.model.WorkerDocument
import at.fhtw.swen3.paperless.ocr.persistence.mapper.WorkerDocumentEntityMapper
import at.fhtw.swen3.paperless.ocr.persistence.repository.OcrDocumentRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.OptimisticLockException
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class JpaOcrDocumentPersistence(
    private val repository: OcrDocumentRepository,
    private val mapper: WorkerDocumentEntityMapper,
    private val entityManager: EntityManager,
) : OcrDocumentPersistence {
    override fun findLockedById(documentId: UUID): WorkerDocument? =
        repository.findLockedById(documentId)?.let(mapper::toModel)

    override fun update(document: WorkerDocument): WorkerDocument {
        val entity = repository.findLockedById(document.id)
            ?: throw OptimisticLockException("Document was deleted concurrently")
        if (entity.version != document.version) {
            throw OptimisticLockException("Document was modified concurrently")
        }
        mapper.applyTo(entity, document)
        entity.updatedAt = Instant.now()
        entityManager.flush()

        return mapper.toModel(entity)
    }
}
