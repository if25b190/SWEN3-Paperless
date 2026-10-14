package at.fhtw.swen3.paperless.document.persistence

import at.fhtw.swen3.paperless.document.model.Document
import java.util.Optional
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface DocumentPersistence {
    fun findById(id: UUID): Optional<Document>
    fun findOwnerOnlyDocuments(ownerId: UUID, documentTypeId: UUID?, pageable: Pageable): Page<Document>
    fun findVisibleDocuments(
        ownerId: UUID, teamIds: Set<UUID>, documentTypeId: UUID?, pageable: Pageable
    ): Page<Document>
    fun findOwnerOnlyDocuments(ownerId: UUID): List<Document>
    fun findVisibleDocuments(ownerId: UUID, teamIds: Set<UUID>): List<Document>
    fun save(document: Document): Document
    fun refreshUnchanged(document: Document): Document
    fun delete(document: Document)
    fun flush()
}
