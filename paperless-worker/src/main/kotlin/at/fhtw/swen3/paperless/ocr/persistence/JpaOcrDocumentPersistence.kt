package at.fhtw.swen3.paperless.ocr.persistence

import at.fhtw.swen3.paperless.ocr.entity.WorkerDocumentEntity
import at.fhtw.swen3.paperless.ocr.model.ProcessingStatus
import at.fhtw.swen3.paperless.ocr.model.WorkerDocument
import at.fhtw.swen3.paperless.ocr.persistence.mapper.WorkerDocumentEntityMapper
import at.fhtw.swen3.paperless.ocr.persistence.repository.OcrDocumentRepository
import jakarta.persistence.EntityManager
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JpaOcrDocumentPersistence(
    private val repository: OcrDocumentRepository,
    private val mapper: WorkerDocumentEntityMapper,
    private val entityManager: EntityManager
) : OcrDocumentPersistence {

    @Transactional
    override fun claim(documentId: UUID): WorkerDocument? {
        val entity = repository.findLockedById(documentId) ?: return null
        if (entity.status != ProcessingStatus.PENDING && entity.status != ProcessingStatus.FAILED) {
            return null
        }
        transition(entity, ProcessingStatus.OCR_IN_PROGRESS, null)
        return mapper.toModel(entity)
    }

    @Transactional
    override fun complete(documentId: UUID, ocrContent: String, status: ProcessingStatus) {
        val entity = repository.findLockedById(documentId) ?: return
        if (entity.status != ProcessingStatus.OCR_IN_PROGRESS) return
        transition(entity, status, ocrContent)
    }

    @Transactional
    override fun fail(documentId: UUID) {
        val entity = repository.findLockedById(documentId) ?: return
        if (entity.status != ProcessingStatus.OCR_IN_PROGRESS) return
        transition(entity, ProcessingStatus.FAILED, null)
    }

    private fun transition(entity: WorkerDocumentEntity, status: ProcessingStatus, ocrContent: String?) {
        mapper.applyTo(entity, WorkerDocument(requireNotNull(entity.id), status, ocrContent, entity.version))
        entity.updatedAt = Instant.now()
        entityManager.flush()
    }
}
