package at.fhtw.swen3.paperless.team.persistence.mapper

import at.fhtw.swen3.paperless.team.model.Team
import at.fhtw.swen3.paperless.team.entity.TeamEntity

object TeamEntityMapper {

    fun toEntity(team: Team): TeamEntity =
        TeamEntity(
            id = team.id,
            name = team.name,
            description = team.description,
            createdAt = team.createdAt,
            updatedAt = team.updatedAt,
            ownerId = team.ownerId,
            version = team.version
        )

    fun toModel(entity: TeamEntity): Team =
        Team(
            id = requireNotNull(entity.id),
            name = entity.name,
            description = entity.description,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            ownerId = entity.ownerId,
            version = entity.version
        )
}
