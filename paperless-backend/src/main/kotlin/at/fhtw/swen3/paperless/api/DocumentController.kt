package at.fhtw.swen3.paperless.document.controller

import at.fhtw.swen3.paperless.api.DocumentsApi
import at.fhtw.swen3.paperless.dto.DocumentResponse
import at.fhtw.swen3.paperless.dto.UpdateDocumentRequest
import at.fhtw.swen3.paperless.document.service.DocumentService
import at.fhtw.swen3.paperless.document.mapper.DocumentMapper
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class DocumentController(
    private val documentService: DocumentService
) : DocumentsApi {

    override fun getDocumentById(id: Long): ResponseEntity<DocumentResponse> {
        val document = documentService.findById(id)
        val dto = DocumentMapper.toDto(document)
        return ResponseEntity.ok(dto)
    }
}
