package at.fhtw.swen3.paperless.document.service

import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.model.DocumentUpdate
import at.fhtw.swen3.paperless.document.model.DocumentUpload
import org.springframework.core.io.Resource
import org.springframework.data.domain.Page

interface DocumentService {

    fun searchDocuments(
        page: Int,
        size: Int,
        sort: String,
        correspondentId: Long?,
        documentTypeId: Long?
    ): Page<Document>

    fun getDocument(id: Long): Document

    fun uploadDocument(upload: DocumentUpload): Document

    fun updateDocument(id: Long, update: DocumentUpdate): Document

    fun deleteDocument(id: Long)

    fun downloadDocument(id: Long): Resource
}
