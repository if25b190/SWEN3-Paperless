package at.fhtw.swen3.paperless.document.persistence

import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.entity.DocumentEntity
import at.fhtw.swen3.paperless.document.persistence.mapper.DocumentEntityMapper
import at.fhtw.swen3.paperless.document.persistence.repository.DocumentRepository
import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.user.entity.UserEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.OptimisticLockException
import java.util.Optional
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class JpaDocumentPersistence(
    private val repository: DocumentRepository,
    private val entityManager: EntityManager,
    private val documentEntityMapper: DocumentEntityMapper
) : DocumentPersistence {

    override fun findById(id: UUID): Optional<Document> = repository.findById(id).map(documentEntityMapper::toModel)

    override fun findOwnerOnlyDocuments(ownerId: UUID, documentTypeId: UUID?, pageable: Pageable): Page<Document> =
        repository.findOwnerOnlyDocuments(ownerId, documentTypeId, pageable).map(documentEntityMapper::toModel)

    override fun findVisibleDocuments(
        ownerId: UUID, teamIds: Set<UUID>, documentTypeId: UUID?, pageable: Pageable
    ): Page<Document> = repository.findVisibleDocuments(ownerId, teamIds, documentTypeId, pageable)
        .map(documentEntityMapper::toModel)

    override fun findOwnerOnlyDocuments(ownerId: UUID): List<Document> =
        repository.findOwnerOnlyDocuments(ownerId).map(documentEntityMapper::toModel)

    override fun findVisibleDocuments(ownerId: UUID, teamIds: Set<UUID>): List<Document> =
        repository.findVisibleDocuments(ownerId, teamIds).map(documentEntityMapper::toModel)

    override fun save(document: Document): Document {
        val references = DocumentEntityMapper.References(
            owner = entityManager.getReference(UserEntity::class.java, document.ownerId),
            team = document.teamId?.let { entityManager.getReference(TeamEntity::class.java, it) },
            documentType = document.documentType?.let {
                entityManager.getReference(DocumentTypeEntity::class.java, requireNotNull(it.id))
            }
        )
        val entity = documentEntityMapper.toEntity(document, references)

        return documentEntityMapper.toModel(repository.save(entity))
    }

    override fun findLockedById(id: UUID): Document? = repository.findLockedById(id)?.let(documentEntityMapper::toModel)

    override fun update(id: UUID, update: Document): Document {
        val entity = repository.findLockedById(id)
            ?: throw OptimisticLockException("Document was deleted concurrently")
        documentEntityMapper.applyTo(entity, update)
        entity.team = update.teamId?.let { entityManager.getReference(TeamEntity::class.java, it) }
        entity.documentType = update.documentType?.id?.let { entityManager.getReference(DocumentTypeEntity::class.java, it) }
        entityManager.flush()

        return documentEntityMapper.toModel(entity)
    }

    override fun delete(document: Document) {
        repository.delete(entityManager.getReference(DocumentEntity::class.java, requireNotNull(document.id)))
    }

    override fun flush() = entityManager.flush()
}
