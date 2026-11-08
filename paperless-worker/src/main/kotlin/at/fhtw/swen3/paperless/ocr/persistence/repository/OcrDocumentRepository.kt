package at.fhtw.swen3.paperless.ocr.persistence.repository

import at.fhtw.swen3.paperless.ocr.entity.WorkerDocumentEntity
import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OcrDocumentRepository : JpaRepository<WorkerDocumentEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from WorkerDocumentEntity d where d.id = :id")
    fun findLockedById(@Param("id") id: UUID): WorkerDocumentEntity?
}
