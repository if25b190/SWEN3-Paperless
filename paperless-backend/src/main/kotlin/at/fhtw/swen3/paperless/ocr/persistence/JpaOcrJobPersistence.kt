package at.fhtw.swen3.paperless.ocr.persistence

import at.fhtw.swen3.paperless.ocr.model.OcrJob
import at.fhtw.swen3.paperless.ocr.persistence.mapper.OcrJobEntityMapper
import at.fhtw.swen3.paperless.ocr.persistence.repository.OcrJobRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JpaOcrJobPersistence(
    private val repository: OcrJobRepository,
    private val mapper: OcrJobEntityMapper
) : OcrJobPersistence {
    override fun enqueue(job: OcrJob) {
        repository.saveAndFlush(mapper.toEntity(job))
    }

    override fun findNextForDispatch(): OcrJob? {
        return repository.findAllByOrderByCreatedAtAsc(PageRequest.of(0, 1)).firstOrNull()
            ?.let(mapper::toModel)
    }

    override fun delete(id: UUID) {
        repository.deleteById(id)
        repository.flush()
    }

}
