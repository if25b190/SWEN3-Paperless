package at.fhtw.swen3.paperless.documenttype.persistence

import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import at.fhtw.swen3.paperless.documenttype.persistence.mapper.DocumentTypeEntityMapper
import at.fhtw.swen3.paperless.documenttype.persistence.repository.DocumentTypeRepository
import java.util.Optional
import java.util.UUID
import org.springframework.stereotype.Repository

@Repository
class JpaDocumentTypePersistence(private val repository: DocumentTypeRepository) : DocumentTypePersistence {

    override fun findAll(): List<DocumentType> = repository.findAll().map(DocumentTypeEntityMapper::toModel)

    override fun findById(id: UUID): Optional<DocumentType> =
        repository.findById(id).map(DocumentTypeEntityMapper::toModel)

    override fun findByName(name: String): DocumentType? =
        repository.findByName(name)?.let(DocumentTypeEntityMapper::toModel)

    override fun save(documentType: DocumentType): DocumentType =
        DocumentTypeEntityMapper.toModel(repository.save(DocumentTypeEntityMapper.toEntity(documentType)))

    override fun delete(documentType: DocumentType) = repository.deleteById(requireNotNull(documentType.id))
}
