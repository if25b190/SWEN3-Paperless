package at.fhtw.swen3.paperless.ocr.persistence.repository

import at.fhtw.swen3.paperless.ocr.entity.OcrJobEntity
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.UUID

interface OcrJobRepository : JpaRepository<OcrJobEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findAllByOrderByCreatedAtAsc(pageable: Pageable): List<OcrJobEntity>
}
