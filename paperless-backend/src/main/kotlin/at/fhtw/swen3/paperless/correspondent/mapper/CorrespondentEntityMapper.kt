package at.fhtw.swen3.paperless.correspondent.mapper

import at.fhtw.swen3.paperless.correspondent.entity.CorrespondentEntity
import at.fhtw.swen3.paperless.correspondent.model.Correspondent

object CorrespondentEntityMapper {

    fun toEntity(correspondent: Correspondent): CorrespondentEntity =
        CorrespondentEntity(
            id = correspondent.id,
            name = correspondent.name,
            notes = correspondent.notes
        )

    fun toModel(entity: CorrespondentEntity): Correspondent =
        Correspondent(
            id = entity.id,
            name = entity.name,
            notes = entity.notes
        )
}
