package at.fhtw.swen3.paperless.document.controller

import at.fhtw.swen3.paperless.api.DocumentsApi
import at.fhtw.swen3.paperless.dto.DocumentResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class DocumentController : DocumentsApi {

    override fun getDocumentById(id: Long): ResponseEntity<DocumentResponse> {
        return ResponseEntity(HttpStatus.NOT_IMPLEMENTED)
    }
}
