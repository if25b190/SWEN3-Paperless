package at.fhtw.swen3.paperless.document.persistence.mapper

import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.document.entity.DocumentEntity
import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.documenttype.persistence.mapper.DocumentTypeEntityMapper
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.user.entity.UserEntity
import org.mapstruct.InjectionStrategy
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingConstants
import org.mapstruct.ReportingPolicy

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = [MappingConverters::class, DocumentTypeEntityMapper::class],
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface DocumentEntityMapper {

    data class References(val owner: UserEntity, val team: TeamEntity?, val documentType: DocumentTypeEntity?)

    @Mapping(target = "id", source = "id", qualifiedByName = ["requireNotNull"])
    @Mapping(target = "ownerId", source = "owner.id", qualifiedByName = ["requireNotNull"])
    @Mapping(target = "teamId", source = "team.id")
    fun toModel(entity: DocumentEntity): Document

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
}
