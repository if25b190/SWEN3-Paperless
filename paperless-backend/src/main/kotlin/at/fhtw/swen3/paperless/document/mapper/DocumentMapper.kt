package at.fhtw.swen3.paperless.document.mapper

import at.fhtw.swen3.paperless.correspondent.mapper.CorrespondentMapper
import at.fhtw.swen3.paperless.dto.DocumentResponse
import at.fhtw.swen3.paperless.dto.ProcessingStatus as DtoProcessingStatus
import at.fhtw.swen3.paperless.dto.UpdateDocumentRequest
import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.model.DocumentUpdate
import at.fhtw.swen3.paperless.documenttype.mapper.DocumentTypeMapper
import java.time.Instant
import java.time.ZoneOffset

object DocumentMapper {

    fun toDto(document: Document): DocumentResponse =
        DocumentResponse(
            id = document.id,
            title = document.title,
            originalFilename = document.originalFilename,
            contentType = document.contentType,
            fileSize = document.fileSize,
            status = DtoProcessingStatus.valueOf(document.status.name),
            createdAt = document.createdAt.atOffset(ZoneOffset.UTC),
            updatedAt = document.updatedAt.atOffset(ZoneOffset.UTC),
            ocrContent = document.ocrContent,
            summary = document.summary,
            storageKey = document.storageKey,
            correspondent = document.correspondent?.let(CorrespondentMapper::toDto),
            documentType = document.documentType?.let(DocumentTypeMapper::toDto)
        )

    // Correspondent / document-type reassignment requires repository lookups and
    // belongs to the service; this maps title and timestamp only.
    fun applyUpdate(document: Document, dto: UpdateDocumentRequest, now: Instant): Document =
        document.copy(
            title = dto.title ?: document.title,
            updatedAt = now
        )

    fun toUpdateModel(dto: UpdateDocumentRequest): DocumentUpdate =
        DocumentUpdate(
            title = dto.title,
            correspondentId = dto.correspondentId,
            documentTypeId = dto.documentTypeId
        )
}
