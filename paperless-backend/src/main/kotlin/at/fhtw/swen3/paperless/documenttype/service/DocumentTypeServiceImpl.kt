package at.fhtw.swen3.paperless.documenttype.service

import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import at.fhtw.swen3.paperless.documenttype.persistence.DocumentTypePersistence
import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class DocumentTypeServiceImpl(
    private val repository: DocumentTypePersistence
) : DocumentTypeService {

    @Transactional(readOnly = true)
    override fun searchDocumentTypes(): List<DocumentType> =
        repository.findAll()

    override fun createDocumentType(documentType: DocumentType): DocumentType {
        if (repository.findByName(documentType.name) != null) {
            throw AppException(AppErrorMessage.DOCUMENT_TYPE_NAME_ALREADY_EXISTS)
        }

        val saved = repository.save(documentType)
        return saved
    }

    @Transactional(readOnly = true)
    override fun getDocumentTypeById(id: UUID): DocumentType =
        findDocumentType(id)

    override fun updateDocumentType(id: UUID, documentType: DocumentType): DocumentType {
        val current = findDocumentType(id)
        val duplicate = repository.findByName(documentType.name)
        if (duplicate != null && duplicate.id != current.id) {
            throw AppException(AppErrorMessage.DOCUMENT_TYPE_NAME_ALREADY_EXISTS)
        }

        val updated = documentType.copy(id = current.id, version = current.version)
        val saved = repository.save(updated)
        return saved
    }

    override fun deleteDocumentType(id: UUID) {
        repository.delete(findDocumentType(id))
    }

    private fun findDocumentType(id: UUID) =
        repository.findById(id).orElseThrow {
            AppException(AppErrorMessage.DOCUMENT_TYPE_NOT_FOUND)
        }
}
