package at.fhtw.swen3.paperless.document.persistence

import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.persistence.entity.DocumentEntity
import at.fhtw.swen3.paperless.document.persistence.mapper.DocumentEntityMapper
import at.fhtw.swen3.paperless.document.persistence.repository.DocumentRepository
import at.fhtw.swen3.paperless.documenttype.persistence.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.team.persistence.entity.TeamEntity
import at.fhtw.swen3.paperless.user.persistence.entity.UserEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityNotFoundException
import jakarta.persistence.OptimisticLockException
import java.util.Optional
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class JpaDocumentPersistence(
    private val repository: DocumentRepository,
    private val entityManager: EntityManager
) : DocumentPersistence {

    override fun findById(id: UUID): Optional<Document> = repository.findById(id).map(DocumentEntityMapper::toModel)

    override fun findOwnerOnlyDocuments(ownerId: UUID, documentTypeId: UUID?, pageable: Pageable): Page<Document> =
        repository.findOwnerOnlyDocuments(ownerId, documentTypeId, pageable).map(DocumentEntityMapper::toModel)

    override fun findVisibleDocuments(
        ownerId: UUID, teamIds: Set<UUID>, documentTypeId: UUID?, pageable: Pageable
    ): Page<Document> = repository.findVisibleDocuments(ownerId, teamIds, documentTypeId, pageable)
        .map(DocumentEntityMapper::toModel)

    override fun findOwnerOnlyDocuments(ownerId: UUID): List<Document> =
        repository.findOwnerOnlyDocuments(ownerId).map(DocumentEntityMapper::toModel)

    override fun findVisibleDocuments(ownerId: UUID, teamIds: Set<UUID>): List<Document> =
        repository.findVisibleDocuments(ownerId, teamIds).map(DocumentEntityMapper::toModel)

    override fun save(document: Document): Document {
        val references = DocumentEntityMapper.References(
            owner = entityManager.getReference(UserEntity::class.java, document.ownerId),
            team = document.teamId?.let { entityManager.getReference(TeamEntity::class.java, it) },
            documentType = document.documentType?.let {
                entityManager.getReference(DocumentTypeEntity::class.java, requireNotNull(it.id))
            }
        )
        val entity = DocumentEntityMapper.toEntity(document, references)

        return DocumentEntityMapper.toModel(repository.save(entity))
    }

    override fun refreshUnchanged(document: Document): Document {
        val entity = repository.findById(requireNotNull(document.id)).orElseThrow {
            OptimisticLockException("Document was deleted concurrently")
        }
        try {
            entityManager.refresh(entity)
        } catch (exception: EntityNotFoundException) {
            throw OptimisticLockException("Document was deleted concurrently", exception)
        }
        val changed = entity.version != document.version || entity.team?.id != document.teamId ||
            entity.owner.id != document.ownerId
        if (changed) {
            throw OptimisticLockException("Document sharing changed concurrently")
        }

        return DocumentEntityMapper.toModel(entity)
    }

    override fun delete(document: Document) {
        repository.delete(entityManager.getReference(DocumentEntity::class.java, requireNotNull(document.id)))
    }

    override fun flush() = entityManager.flush()
}
