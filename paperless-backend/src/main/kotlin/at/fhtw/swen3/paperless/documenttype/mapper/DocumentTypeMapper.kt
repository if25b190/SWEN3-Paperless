package at.fhtw.swen3.paperless.documenttype.mapper

import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import at.fhtw.swen3.paperless.dto.CreateDocumentTypeRequest
import at.fhtw.swen3.paperless.dto.DocumentTypeResponse
import at.fhtw.swen3.paperless.dto.UpdateDocumentTypeRequest
import org.mapstruct.InjectionStrategy
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingConstants
import org.mapstruct.ReportingPolicy

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = [MappingConverters::class],
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface DocumentTypeMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = ["requireNotNull"])
    fun toDto(documentType: DocumentType): DocumentTypeResponse

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
