package at.fhtw.swen3.paperless.document.mapper

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
            ownerId = document.ownerId,
            teamId = document.teamId,
            status = DtoProcessingStatus.valueOf(document.status.name),
            createdAt = document.createdAt.atOffset(ZoneOffset.UTC),
            updatedAt = document.updatedAt.atOffset(ZoneOffset.UTC),
            ocrContent = document.ocrContent,
            summary = document.summary,
            storageKey = document.storageKey,
            documentType = document.documentType?.let(DocumentTypeMapper::toDto)
        )

    // Document-type reassignment requires a repository lookup and belongs to the service.
    fun applyUpdate(document: Document, dto: UpdateDocumentRequest, now: Instant): Document =
        document.copy(
            title = dto.title ?: document.title,
            updatedAt = now
        )

    fun toUpdateModel(dto: UpdateDocumentRequest): DocumentUpdate =
        DocumentUpdate(
            teamId = dto.teamId,
            clearTeam = dto.clearTeam == true,
            title = dto.title,
            documentTypeId = dto.documentTypeId
        )
}
