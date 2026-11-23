package at.fhtw.swen3.paperless.ocr.persistence.repository

import at.fhtw.swen3.paperless.ocr.entity.OcrJobEntity
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface OcrJobRepository : JpaRepository<OcrJobEntity, UUID> {
    fun findByAvailableAtLessThanEqualOrderByCreatedAtAsc(now: Instant, pageable: Pageable): List<OcrJobEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from OcrJobEntity j where j.id = :id")
    fun findLockedById(@Param("id") id: UUID): OcrJobEntity?
}
