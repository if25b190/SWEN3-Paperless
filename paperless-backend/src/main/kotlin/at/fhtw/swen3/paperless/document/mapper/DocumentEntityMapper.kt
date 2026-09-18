package at.fhtw.swen3.paperless.document.mapper

import at.fhtw.swen3.paperless.correspondent.entity.CorrespondentEntity
import at.fhtw.swen3.paperless.correspondent.mapper.CorrespondentEntityMapper
import at.fhtw.swen3.paperless.document.entity.DocumentEntity
import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.documenttype.mapper.DocumentTypeEntityMapper

object DocumentEntityMapper {

    fun toEntity(document: Document): DocumentEntity =
        DocumentEntity(
            id = document.id,
            title = document.title,
            originalFilename = document.originalFilename,
            contentType = document.contentType,
            fileSize = document.fileSize,
            status = document.status,
            ocrContent = document.ocrContent,
            summary = document.summary,
            storageKey = document.storageKey,
            correspondent = document.correspondent?.let { CorrespondentEntity(id = it.id) },
            documentType = document.documentType?.let { DocumentTypeEntity(id = it.id) },
            createdAt = document.createdAt,
            updatedAt = document.updatedAt
        )

    fun toModel(entity: DocumentEntity): Document =
        Document(
            id = entity.id,
            title = entity.title,
            originalFilename = entity.originalFilename,
            contentType = entity.contentType,
            fileSize = entity.fileSize,
            status = entity.status,
            ocrContent = entity.ocrContent,
            summary = entity.summary,
            storageKey = entity.storageKey,
            correspondent = entity.correspondent?.let(CorrespondentEntityMapper::toModel),
            documentType = entity.documentType?.let(DocumentTypeEntityMapper::toModel),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
}
