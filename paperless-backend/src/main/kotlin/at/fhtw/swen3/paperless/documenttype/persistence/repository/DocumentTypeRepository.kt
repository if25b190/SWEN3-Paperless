package at.fhtw.swen3.paperless.documenttype.persistence.repository

import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface DocumentTypeRepository : JpaRepository<DocumentTypeEntity, UUID> {

    fun findByName(name: String): DocumentTypeEntity?
}
