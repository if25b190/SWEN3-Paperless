package at.fhtw.swen3.paperless.document.mapper

import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.model.DocumentUpdate
import at.fhtw.swen3.paperless.documenttype.mapper.DocumentTypeMapper
import at.fhtw.swen3.paperless.dto.DocumentResponse
import at.fhtw.swen3.paperless.dto.UpdateDocumentRequest
import org.mapstruct.InjectionStrategy
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingConstants
import org.mapstruct.ReportingPolicy

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = [MappingConverters::class, DocumentTypeMapper::class],
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface DocumentMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = ["requireNotNull"])
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = ["toOffset"])
    @Mapping(target = "updatedAt", source = "updatedAt", qualifiedByName = ["toOffset"])
    fun toDto(document: Document): DocumentResponse

    fun toUpdateModel(dto: UpdateDocumentRequest): DocumentUpdate
}
