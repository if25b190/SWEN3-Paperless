package at.fhtw.swen3.paperless.documenttype.persistence.mapper

import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import org.mapstruct.InjectionStrategy
import org.mapstruct.Mapper
import org.mapstruct.MappingConstants
import org.mapstruct.ReportingPolicy

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = [MappingConverters::class],
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface DocumentTypeEntityMapper {

    fun toModel(entity: DocumentTypeEntity): DocumentType

    fun toEntity(documentType: DocumentType): DocumentTypeEntity =
        DocumentTypeEntity(
            id = documentType.id,
            name = documentType.name,
            description = documentType.description,
            version = documentType.version
        )
}
