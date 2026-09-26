package at.fhtw.swen3.paperless.documenttype.mapper

import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import at.fhtw.swen3.paperless.dto.CreateDocumentTypeRequest
import at.fhtw.swen3.paperless.dto.DocumentTypeResponse
import at.fhtw.swen3.paperless.dto.UpdateDocumentTypeRequest

object DocumentTypeMapper {

    fun toDto(documentType: DocumentType): DocumentTypeResponse =
        DocumentTypeResponse(
            id = requireNotNull(documentType.id) { "Cannot map an unpersisted document type to a response" },
            name = documentType.name,
            description = documentType.description
        )

    fun fromCreateDto(dto: CreateDocumentTypeRequest): DocumentType =
        DocumentType(
            id = null,
            name = dto.name,
            description = dto.description
        )

    fun applyUpdate(documentType: DocumentType, dto: UpdateDocumentTypeRequest): DocumentType =
        documentType.copy(
            name = dto.name ?: documentType.name,
            description = dto.description ?: documentType.description
        )
}
