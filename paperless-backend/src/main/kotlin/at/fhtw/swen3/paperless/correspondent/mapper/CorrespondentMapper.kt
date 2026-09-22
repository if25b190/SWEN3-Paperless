package at.fhtw.swen3.paperless.correspondent.mapper

import at.fhtw.swen3.paperless.dto.CorrespondentResponse
import at.fhtw.swen3.paperless.dto.CreateCorrespondentRequest
import at.fhtw.swen3.paperless.dto.UpdateCorrespondentRequest
import at.fhtw.swen3.paperless.correspondent.model.Correspondent

object CorrespondentMapper {

    fun toDto(correspondent: Correspondent): CorrespondentResponse =
        CorrespondentResponse(
            id = correspondent.id,
            name = correspondent.name,
            notes = correspondent.notes
        )

    fun fromCreateDto(dto: CreateCorrespondentRequest): Correspondent =
        Correspondent(
            id = 0,
            name = dto.name,
            notes = dto.notes
        )

    fun applyUpdate(correspondent: Correspondent, dto: UpdateCorrespondentRequest): Correspondent =
        correspondent.copy(
            name = dto.name ?: correspondent.name,
            notes = dto.notes ?: correspondent.notes
        )
}
