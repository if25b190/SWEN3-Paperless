package at.fhtw.swen3.paperless.team.mapper

import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.team.model.Team

object TeamEntityMapper {

    fun toEntity(team: Team): TeamEntity =
        TeamEntity(
            id = team.id,
            name = team.name,
            description = team.description,
            createdAt = team.createdAt,
            updatedAt = team.updatedAt
        )

    fun toModel(entity: TeamEntity): Team =
        Team(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
}
