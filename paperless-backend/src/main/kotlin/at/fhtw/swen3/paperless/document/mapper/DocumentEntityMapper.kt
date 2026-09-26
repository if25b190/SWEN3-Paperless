package at.fhtw.swen3.paperless.document.mapper

import at.fhtw.swen3.paperless.document.entity.DocumentEntity
import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.documenttype.mapper.DocumentTypeEntityMapper
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.user.entity.UserEntity

object DocumentEntityMapper {

    fun toEntity(document: Document): DocumentEntity =
        DocumentEntity(
            id = document.id,
            title = document.title,
            originalFilename = document.originalFilename,
            contentType = document.contentType,
            fileSize = document.fileSize,
            owner = UserEntity(id = document.ownerId),
            team = document.teamId?.let { TeamEntity(id = it) },
            status = document.status,
            ocrContent = document.ocrContent,
            summary = document.summary,
            storageKey = document.storageKey,
            documentType = document.documentType?.let { DocumentTypeEntity(id = it.id) },
            createdAt = document.createdAt,
            updatedAt = document.updatedAt
        )

    fun toModel(entity: DocumentEntity): Document =
        Document(
            id = requireNotNull(entity.id) { "Persisted document must have an ID" },
            title = entity.title,
            originalFilename = entity.originalFilename,
            contentType = entity.contentType,
            fileSize = entity.fileSize,
            ownerId = requireNotNull(entity.owner.id) { "Persisted document owner must have an ID" },
            teamId = entity.team?.id,
            status = entity.status,
            ocrContent = entity.ocrContent,
            summary = entity.summary,
            storageKey = entity.storageKey,
            documentType = entity.documentType?.let(DocumentTypeEntityMapper::toModel),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
}
