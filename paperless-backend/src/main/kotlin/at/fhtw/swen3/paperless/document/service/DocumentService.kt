package at.fhtw.swen3.paperless.document.service

import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.model.DocumentUpdate
import at.fhtw.swen3.paperless.document.model.DocumentUpload
import org.springframework.core.io.Resource
import org.springframework.data.domain.Page
import java.util.UUID

interface DocumentService {

    fun searchDocuments(
        page: Int,
        size: Int,
        sort: String,
        documentTypeId: UUID?
    ): Page<Document>

    fun visibleDocumentsForSearch(): List<Document>

    fun getDocument(id: UUID): Document

    fun uploadDocument(upload: DocumentUpload): Document

    fun updateDocument(id: UUID, update: DocumentUpdate): Document

    fun deleteDocument(id: UUID)

    fun downloadDocument(id: UUID): Resource
}
