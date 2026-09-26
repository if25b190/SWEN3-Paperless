package at.fhtw.swen3.paperless.documenttype.service

import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import java.util.UUID

interface DocumentTypeService {

    fun searchDocumentTypes(): List<DocumentType>

    fun createDocumentType(documentType: DocumentType): DocumentType

    fun getDocumentTypeById(id: UUID): DocumentType

    fun updateDocumentType(id: UUID, documentType: DocumentType): DocumentType

    fun deleteDocumentType(id: UUID)
}
