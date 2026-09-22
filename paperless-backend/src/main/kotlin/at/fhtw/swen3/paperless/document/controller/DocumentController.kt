package at.fhtw.swen3.paperless.document.controller

import at.fhtw.swen3.paperless.api.DocumentsApi
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
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.net.URI

@RestController
class DocumentController(private val documentService: DocumentService) : DocumentsApi {

    override fun getDocumentById(id: Long): ResponseEntity<DocumentResponse> {
        val document = documentService.getDocument(id)
        val response = DocumentMapper.toDto(document)
        return ResponseEntity.ok(response)
    }

    override fun searchDocuments(
        page: Int,
        size: Int,
        sort: String,
        correspondentId: Long?,
        documentTypeId: Long?
    ): ResponseEntity<DocumentPageResponse> {
        val documents = documentService.searchDocuments(page, size, sort, correspondentId, documentTypeId)
        val items = documents.content.map(DocumentMapper::toDto)
        val pagination = PageMetadata(page, size, documents.totalElements, documents.totalPages)
        return ResponseEntity.ok(DocumentPageResponse(pagination, items))
    }

    override fun uploadDocument(
        document: MultipartFile,
        title: String,
        correspondentId: Long?,
        documentTypeId: Long?
    ): ResponseEntity<DocumentResponse> {
        val upload = DocumentUpload(
            title = title,
            originalFilename = document.originalFilename ?: "document",
            contentType = document.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE,
            content = document.bytes,
            correspondentId = correspondentId,
            documentTypeId = documentTypeId
        )
        val created = documentService.uploadDocument(upload)
        val response = DocumentMapper.toDto(created)
        return ResponseEntity.created(URI.create("/documents/${created.id}")).body(response)
    }

    override fun updateDocumentMetadata(
        id: Long,
        updateDocumentRequest: UpdateDocumentRequest
    ): ResponseEntity<DocumentResponse> {
        val update = DocumentMapper.toUpdateModel(updateDocumentRequest)
        val document = documentService.updateDocument(id, update)
        val response = DocumentMapper.toDto(document)
        return ResponseEntity.ok(response)
    }

    override fun deleteDocument(id: Long): ResponseEntity<Unit> {
        documentService.deleteDocument(id)
        return ResponseEntity.noContent().build()
    }

    override fun downloadDocumentFile(id: Long): ResponseEntity<Resource> {
        val resource = documentService.downloadDocument(id)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${resource.filename}\"")
            .body(resource)
    }
}
