package at.fhtw.swen3.paperless.document.service

import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.model.DocumentUpdate
import at.fhtw.swen3.paperless.document.model.DocumentUpload
import java.util.UUID
import org.springframework.core.io.Resource
import org.springframework.data.domain.Page

interface DocumentService {

    fun searchDocuments(
        actorId: UUID,
        page: Int,
        size: Int,
        sort: String,
        documentTypeId: UUID?
    ): Page<Document>

    fun getDocument(actorId: UUID, id: UUID): Document

    fun uploadDocument(actorId: UUID, upload: DocumentUpload): Document

    fun updateDocument(actorId: UUID, id: UUID, update: DocumentUpdate): Document

    fun deleteDocument(actorId: UUID, id: UUID)

    fun downloadDocument(actorId: UUID, id: UUID): Resource
}
