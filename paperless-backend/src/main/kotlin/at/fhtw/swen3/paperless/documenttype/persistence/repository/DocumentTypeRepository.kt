package at.fhtw.swen3.paperless.documenttype.persistence.repository

import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DocumentTypeRepository : JpaRepository<DocumentTypeEntity, UUID> {

    fun findByName(name: String): DocumentTypeEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DocumentTypeEntity d where d.id = :id")
    fun findLockedById(@Param("id") id: UUID): DocumentTypeEntity?
}
