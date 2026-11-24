package at.fhtw.swen3.paperless.documenttype.service

import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import at.fhtw.swen3.paperless.documenttype.model.DocumentTypeUpdate
import at.fhtw.swen3.paperless.documenttype.persistence.DocumentTypePersistence
import at.fhtw.swen3.paperless.exception.DocumentTypeNameAlreadyExistsException
import at.fhtw.swen3.paperless.exception.DocumentTypeNotFoundException
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class DocumentTypeServiceImpl(
    private val repository: DocumentTypePersistence
) : DocumentTypeService {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    override fun searchDocumentTypes(): List<DocumentType> =
        repository.findAll()

    override fun createDocumentType(documentType: DocumentType): DocumentType {
        if (repository.findByName(documentType.name) != null) {
            throw DocumentTypeNameAlreadyExistsException()
        }

        val saved = repository.save(documentType)
        log.info("[DOCUMENT_TYPE] - ACTION: createDocumentType: documentTypeId: {}", saved.id)

        return saved
    }

    @Transactional(readOnly = true)
    override fun getDocumentTypeById(id: UUID): DocumentType =
        findDocumentType(id)

    override fun updateDocumentType(id: UUID, update: DocumentTypeUpdate): DocumentType {
        val current = repository.findLockedById(id)
            ?: throw DocumentTypeNotFoundException()
        val updated = current.copy(
            name = update.name ?: current.name,
            description = update.description ?: current.description,
        )
        val duplicate = repository.findByName(updated.name)
        if (duplicate != null && duplicate.id != current.id) {
            throw DocumentTypeNameAlreadyExistsException()
        }

        log.info("[DOCUMENT_TYPE] - ACTION: updateDocumentType: documentTypeId: {}", id)

        return repository.update(id, updated)
    }

    override fun deleteDocumentType(id: UUID) {
        repository.delete(findDocumentType(id))
        log.info("[DOCUMENT_TYPE] - ACTION: deleteDocumentType: documentTypeId: {}", id)
    }

    private fun findDocumentType(id: UUID) =
        repository.findById(id).orElseThrow { DocumentTypeNotFoundException() }
}
