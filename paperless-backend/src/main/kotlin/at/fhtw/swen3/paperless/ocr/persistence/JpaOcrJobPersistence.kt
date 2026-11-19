package at.fhtw.swen3.paperless.ocr.persistence

import at.fhtw.swen3.paperless.ocr.model.OcrJob
import at.fhtw.swen3.paperless.ocr.persistence.mapper.OcrJobEntityMapper
import at.fhtw.swen3.paperless.ocr.persistence.repository.OcrJobRepository
import jakarta.persistence.EntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class JpaOcrJobPersistence(
    private val repository: OcrJobRepository,
    private val mapper: OcrJobEntityMapper,
    private val entityManager: EntityManager,
) : OcrJobPersistence {
    override fun enqueue(job: OcrJob) {
        repository.saveAndFlush(mapper.toEntity(job))
    }

    override fun findNextForDispatch(): OcrJob? {
        val entity = repository.findByAvailableAtLessThanEqualOrderByCreatedAtAsc(Instant.now(), PageRequest.of(0, 1))
            .firstOrNull() ?: return null
        val candidate = mapper.toModel(entity)
        // Another dispatcher may change this row while we wait for the document lock.
        entityManager.detach(entity)

        return candidate
    }

    override fun findLockedById(id: UUID): OcrJob? {
        val entity = repository.findLockedById(id) ?: return null
        entityManager.refresh(entity)

        return mapper.toModel(entity)
    }

    override fun update(job: OcrJob) {
        val entity = requireNotNull(repository.findLockedById(requireNotNull(job.id)))
        entity.attemptId = job.attemptId
        entity.attempts = job.attempts
        entity.availableAt = job.availableAt
        repository.flush()
    }

    override fun delete(id: UUID) {
        repository.deleteById(id)
        repository.flush()
    }

}
