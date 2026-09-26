package at.fhtw.swen3.paperless.documenttype.controller

import at.fhtw.swen3.paperless.api.DocumentTypesApi
import at.fhtw.swen3.paperless.documenttype.mapper.DocumentTypeMapper
import at.fhtw.swen3.paperless.documenttype.service.DocumentTypeService
import at.fhtw.swen3.paperless.dto.CreateDocumentTypeRequest
import at.fhtw.swen3.paperless.dto.DocumentTypeListResponse
import at.fhtw.swen3.paperless.dto.DocumentTypeResponse
import at.fhtw.swen3.paperless.dto.UpdateDocumentTypeRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
class DocumentTypeController(
    private val service: DocumentTypeService
) : DocumentTypesApi {

    override fun searchDocumentTypes(): ResponseEntity<DocumentTypeListResponse> {
        val documentTypes = service.searchDocumentTypes()
        val items = documentTypes.map(DocumentTypeMapper::toDto)
        return ResponseEntity.ok(DocumentTypeListResponse(items = items))
    }

    override fun createDocumentType(
        createDocumentTypeRequest: CreateDocumentTypeRequest
    ): ResponseEntity<DocumentTypeResponse> {
        val documentType = DocumentTypeMapper.fromCreateDto(createDocumentTypeRequest)
        val created = service.createDocumentType(documentType)
        val response = DocumentTypeMapper.toDto(created)
        return ResponseEntity.created(URI.create("/document-types/${created.id}")).body(response)
    }

    override fun getDocumentTypeById(id: UUID): ResponseEntity<DocumentTypeResponse> {
        val documentType = service.getDocumentTypeById(id)
        val response = DocumentTypeMapper.toDto(documentType)
        return ResponseEntity.ok(response)
    }

    override fun updateDocumentType(
        id: UUID,
        updateDocumentTypeRequest: UpdateDocumentTypeRequest
    ): ResponseEntity<DocumentTypeResponse> {
        val current = service.getDocumentTypeById(id)
        val documentType = DocumentTypeMapper.applyUpdate(current, updateDocumentTypeRequest)
        val updated = service.updateDocumentType(id, documentType)
        val response = DocumentTypeMapper.toDto(updated)
        return ResponseEntity.ok(response)
    }

    override fun deleteDocumentType(id: UUID): ResponseEntity<Unit> {
        service.deleteDocumentType(id)
        return ResponseEntity.noContent().build()
    }
}
