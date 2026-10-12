package at.fhtw.swen3.paperless.document.persistence.mapper

import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.persistence.entity.DocumentEntity
import at.fhtw.swen3.paperless.documenttype.persistence.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.documenttype.persistence.mapper.DocumentTypeEntityMapper
import at.fhtw.swen3.paperless.team.persistence.entity.TeamEntity
import at.fhtw.swen3.paperless.user.persistence.entity.UserEntity

object DocumentEntityMapper {

    data class References(val owner: UserEntity, val team: TeamEntity?, val documentType: DocumentTypeEntity?)

    fun toEntity(document: Document, references: References): DocumentEntity =
        DocumentEntity(
            id = document.id,
            title = document.title,
            originalFilename = document.originalFilename,
            contentType = document.contentType,
            fileSize = document.fileSize,
            owner = references.owner,
            team = references.team,
            status = document.status,
            ocrContent = document.ocrContent,
            summary = document.summary,
            storageKey = document.storageKey,
            documentType = references.documentType,
            createdAt = document.createdAt,
            updatedAt = document.updatedAt,
            version = document.version
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
            updatedAt = entity.updatedAt,
            version = entity.version
        )
}
