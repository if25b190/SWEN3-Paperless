package at.fhtw.swen3.paperless.documenttype.repository

import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import org.springframework.data.jpa.repository.JpaRepository

interface DocumentTypeRepository : JpaRepository<DocumentTypeEntity, Long> {

    fun findByName(name: String): DocumentTypeEntity?
}
