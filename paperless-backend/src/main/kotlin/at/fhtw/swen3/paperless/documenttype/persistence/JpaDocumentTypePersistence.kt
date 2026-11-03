package at.fhtw.swen3.paperless.documenttype.persistence

import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import at.fhtw.swen3.paperless.documenttype.persistence.mapper.DocumentTypeEntityMapper
import at.fhtw.swen3.paperless.documenttype.persistence.repository.DocumentTypeRepository
import java.util.Optional
import java.util.UUID
import org.springframework.stereotype.Repository
import jakarta.persistence.EntityManager

@Repository
class JpaDocumentTypePersistence(
    private val repository: DocumentTypeRepository,
    private val documentTypeEntityMapper: DocumentTypeEntityMapper,
    private val entityManager: EntityManager,
) : DocumentTypePersistence {

    override fun findAll(): List<DocumentType> = repository.findAll().map(documentTypeEntityMapper::toModel)

    override fun findById(id: UUID): Optional<DocumentType> =
        repository.findById(id).map(documentTypeEntityMapper::toModel)

    override fun findByName(name: String): DocumentType? =
        repository.findByName(name)?.let(documentTypeEntityMapper::toModel)

    override fun save(documentType: DocumentType): DocumentType =
        documentTypeEntityMapper.toModel(repository.save(documentTypeEntityMapper.toEntity(documentType)))

    override fun findLockedById(id: UUID): DocumentType? =
        repository.findLockedById(id)?.let(documentTypeEntityMapper::toModel)

    override fun update(id: UUID, update: DocumentType): DocumentType {
        val entity = repository.findLockedById(id) ?: throw NoSuchElementException("DocumentType $id not found")
        documentTypeEntityMapper.applyTo(entity, update)
        entityManager.flush()

        return documentTypeEntityMapper.toModel(entity)
    }

    override fun delete(documentType: DocumentType) = repository.deleteById(requireNotNull(documentType.id))
}
