package at.fhtw.swen3.paperless.document.repository

import at.fhtw.swen3.paperless.document.entity.DocumentEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface DocumentRepository : JpaRepository<DocumentEntity, Long> {

    fun findByCorrespondent_Id(correspondentId: Long, pageable: Pageable): Page<DocumentEntity>

    fun findByDocumentType_Id(documentTypeId: Long, pageable: Pageable): Page<DocumentEntity>

    fun findByCorrespondent_IdAndDocumentType_Id(
        correspondentId: Long,
        documentTypeId: Long,
        pageable: Pageable
    ): Page<DocumentEntity>
}
