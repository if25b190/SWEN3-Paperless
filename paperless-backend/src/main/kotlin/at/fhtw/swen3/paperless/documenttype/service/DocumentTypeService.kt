package at.fhtw.swen3.paperless.documenttype.service

import at.fhtw.swen3.paperless.documenttype.model.DocumentType

interface DocumentTypeService {

    fun searchDocumentTypes(): List<DocumentType>

    fun createDocumentType(documentType: DocumentType): DocumentType

    fun getDocumentTypeById(id: Long): DocumentType

    fun updateDocumentType(id: Long, documentType: DocumentType): DocumentType

    fun deleteDocumentType(id: Long)
}
