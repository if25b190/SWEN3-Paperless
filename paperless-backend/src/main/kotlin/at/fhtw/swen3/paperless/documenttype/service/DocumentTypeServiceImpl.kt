package at.fhtw.swen3.paperless.documenttype.service

import at.fhtw.swen3.paperless.documenttype.mapper.DocumentTypeEntityMapper
import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import at.fhtw.swen3.paperless.documenttype.repository.DocumentTypeRepository
import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class DocumentTypeServiceImpl(
    private val repository: DocumentTypeRepository
) : DocumentTypeService {

    @Transactional(readOnly = true)
    override fun searchDocumentTypes(): List<DocumentType> =
        repository.findAll().map(DocumentTypeEntityMapper::toModel)

    override fun createDocumentType(documentType: DocumentType): DocumentType {
        if (repository.findByName(documentType.name) != null) {
            throw AppException(AppErrorMessage.DOCUMENT_TYPE_NAME_ALREADY_EXISTS)
        }

        val saved = repository.save(DocumentTypeEntityMapper.toEntity(documentType))
        return DocumentTypeEntityMapper.toModel(saved)
    }

    @Transactional(readOnly = true)
    override fun getDocumentTypeById(id: UUID): DocumentType =
        DocumentTypeEntityMapper.toModel(findEntity(id))

    override fun updateDocumentType(id: UUID, documentType: DocumentType): DocumentType {
        val current = findEntity(id)
        val duplicate = repository.findByName(documentType.name)
        if (duplicate != null && duplicate.id != current.id) {
            throw AppException(AppErrorMessage.DOCUMENT_TYPE_NAME_ALREADY_EXISTS)
        }

        val updated = documentType.copy(id = current.id)
        val saved = repository.save(DocumentTypeEntityMapper.toEntity(updated))
        return DocumentTypeEntityMapper.toModel(saved)
    }

    override fun deleteDocumentType(id: UUID) {
        repository.delete(findEntity(id))
    }

    private fun findEntity(id: UUID) =
        repository.findById(id).orElseThrow {
            AppException(AppErrorMessage.DOCUMENT_TYPE_NOT_FOUND)
        }
}
