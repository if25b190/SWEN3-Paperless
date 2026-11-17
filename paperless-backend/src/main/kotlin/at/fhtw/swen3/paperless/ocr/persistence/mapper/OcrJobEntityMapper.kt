package at.fhtw.swen3.paperless.ocr.persistence.mapper

import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.ocr.entity.OcrJobEntity
import at.fhtw.swen3.paperless.ocr.model.OcrJob
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
interface OcrJobEntityMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = ["requireNotNull"])
    fun toModel(entity: OcrJobEntity): OcrJob

    fun toEntity(job: OcrJob): OcrJobEntity =
        OcrJobEntity(
            documentId = job.documentId,
            storageKey = job.storageKey,
            contentType = job.contentType
        )
}
