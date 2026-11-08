package at.fhtw.swen3.paperless.ocr.persistence.mapper

import at.fhtw.swen3.paperless.ocr.entity.WorkerDocumentEntity
import at.fhtw.swen3.paperless.ocr.model.WorkerDocument
import java.util.UUID
import org.mapstruct.InjectionStrategy
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.mapstruct.MappingConstants
import org.mapstruct.Named
import org.mapstruct.ReportingPolicy

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface WorkerDocumentEntityMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = ["requireNotNull"])
    fun toModel(entity: WorkerDocumentEntity): WorkerDocument

    fun toEntity(document: WorkerDocument): WorkerDocumentEntity =
        WorkerDocumentEntity(
            id = document.id,
            status = document.status,
            ocrContent = document.ocrContent
        )

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    fun applyTo(@MappingTarget target: WorkerDocumentEntity, source: WorkerDocument): WorkerDocumentEntity

    @Named("requireNotNull")
    fun requireNotNull(value: UUID?): UUID = value ?: throw IllegalStateException("null id")
}
