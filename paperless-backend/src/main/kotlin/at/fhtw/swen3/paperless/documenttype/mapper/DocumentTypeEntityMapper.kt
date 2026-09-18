package at.fhtw.swen3.paperless.documenttype.mapper

import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.documenttype.model.DocumentType

object DocumentTypeEntityMapper {

    fun toEntity(documentType: DocumentType): DocumentTypeEntity =
        DocumentTypeEntity(
            id = documentType.id,
            name = documentType.name,
            description = documentType.description
        )

    fun toModel(entity: DocumentTypeEntity): DocumentType =
        DocumentType(
            id = entity.id,
            name = entity.name,
            description = entity.description
        )
}
