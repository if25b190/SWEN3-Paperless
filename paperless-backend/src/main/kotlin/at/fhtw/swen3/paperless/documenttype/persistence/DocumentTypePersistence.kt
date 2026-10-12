package at.fhtw.swen3.paperless.documenttype.persistence

import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import java.util.Optional
import java.util.UUID

interface DocumentTypePersistence {
    fun findAll(): List<DocumentType>
    fun findById(id: UUID): Optional<DocumentType>
    fun findByName(name: String): DocumentType?
    fun save(documentType: DocumentType): DocumentType
    fun delete(documentType: DocumentType)
}
