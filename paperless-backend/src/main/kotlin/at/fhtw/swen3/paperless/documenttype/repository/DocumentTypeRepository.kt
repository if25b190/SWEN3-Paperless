package at.fhtw.swen3.paperless.documenttype.repository

import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DocumentTypeRepository : JpaRepository<DocumentTypeEntity, UUID> {

    fun findByName(name: String): DocumentTypeEntity?
}
