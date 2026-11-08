package at.fhtw.swen3.paperless.ocr.mapper

import at.fhtw.swen3.paperless.ocr.dto.OcrRequestDTO
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import org.mapstruct.InjectionStrategy
import org.mapstruct.Mapper
import org.mapstruct.MappingConstants
import org.mapstruct.ReportingPolicy

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface OcrRequestMapper {
    fun fromDto(dto: OcrRequestDTO): OcrRequest
}
