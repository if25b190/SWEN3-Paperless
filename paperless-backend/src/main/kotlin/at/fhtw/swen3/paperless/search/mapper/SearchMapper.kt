package at.fhtw.swen3.paperless.search.mapper

import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.document.mapper.DocumentMapper
import at.fhtw.swen3.paperless.dto.SearchResultItem
import at.fhtw.swen3.paperless.search.model.SearchResult
import org.mapstruct.InjectionStrategy
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingConstants
import org.mapstruct.Named
import org.mapstruct.ReportingPolicy

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = [MappingConverters::class, DocumentMapper::class],
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface SearchMapper {

    @Mapping(target = "highlights", source = "highlights", qualifiedByName = ["nullIfEmpty"])
    fun toDto(result: SearchResult): SearchResultItem

    @Named("nullIfEmpty")
    fun nullIfEmpty(highlights: List<String>?): List<String>? =
        if (highlights.isNullOrEmpty()) null else highlights
}
