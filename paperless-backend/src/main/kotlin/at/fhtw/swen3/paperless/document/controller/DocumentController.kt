package at.fhtw.swen3.paperless.document.controller

import at.fhtw.swen3.paperless.api.DocumentsApi
import at.fhtw.swen3.paperless.auth.model.AuthenticatedPrincipal
import at.fhtw.swen3.paperless.dto.DocumentDetailResponse
import at.fhtw.swen3.paperless.dto.DocumentResponse
import at.fhtw.swen3.paperless.dto.DocumentPageResponse
import at.fhtw.swen3.paperless.dto.UpdateDocumentRequest
import at.fhtw.swen3.paperless.document.mapper.DocumentMapper
import at.fhtw.swen3.paperless.document.model.DocumentUpload
import at.fhtw.swen3.paperless.document.service.DocumentService
import at.fhtw.swen3.paperless.dto.PageMetadata
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.net.URI
import java.util.UUID

@RestController
class DocumentController(
    private val documentService: DocumentService,
    private val documentMapper: DocumentMapper
) : DocumentsApi {

    override fun getDocumentById(id: UUID, authentication: Authentication?): ResponseEntity<DocumentDetailResponse> {
        val document = documentService.getDocument(AuthenticatedPrincipal.userId(authentication), id)
        val response = documentMapper.toDetailDto(document)
        return ResponseEntity.ok(response)
    }

    override fun searchDocuments(
        page: Int,
        size: Int,
        sort: String,
        documentTypeId: UUID?,
        authentication: Authentication?
    ): ResponseEntity<DocumentPageResponse> {
        val documents = documentService.searchDocuments(AuthenticatedPrincipal.userId(authentication), page, size, sort, documentTypeId)
        val items = documents.content.map(documentMapper::toDto)
        val pagination = PageMetadata(page, size, documents.totalElements, documents.totalPages)
        return ResponseEntity.ok(DocumentPageResponse(pagination, items))
    }

    override fun uploadDocument(
        document: MultipartFile,
        title: String,
        documentTypeId: UUID?,
        teamId: UUID?,
        authentication: Authentication?
    ): ResponseEntity<DocumentResponse> {
        val actorId = AuthenticatedPrincipal.userId(authentication)
        val upload = DocumentUpload(
            title = title,
            originalFilename = document.originalFilename ?: "document",
            contentType = document.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE,
            content = document.bytes,
            documentTypeId = documentTypeId,
            teamId = teamId
        )
        val created = documentService.uploadDocument(actorId, upload)
        val response = documentMapper.toDto(created)
        return ResponseEntity.created(URI.create("/api/v1/documents/${created.id}")).body(response)
    }

    override fun updateDocumentMetadata(
        id: UUID,
        updateDocumentRequest: UpdateDocumentRequest,
        authentication: Authentication?
    ): ResponseEntity<DocumentResponse> {
        val update = documentMapper.toUpdateModel(updateDocumentRequest)
        val document = documentService.updateDocument(AuthenticatedPrincipal.userId(authentication), id, update)
        val response = documentMapper.toDto(document)
        return ResponseEntity.ok(response)
    }

    override fun deleteDocument(id: UUID, authentication: Authentication?): ResponseEntity<Unit> {
        documentService.deleteDocument(AuthenticatedPrincipal.userId(authentication), id)
        return ResponseEntity.noContent().build()
    }

    override fun downloadDocumentFile(id: UUID, authentication: Authentication?): ResponseEntity<Resource> {
        val resource = documentService.downloadDocument(AuthenticatedPrincipal.userId(authentication), id)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${resource.filename}\"")
            .body(resource)
    }
}
