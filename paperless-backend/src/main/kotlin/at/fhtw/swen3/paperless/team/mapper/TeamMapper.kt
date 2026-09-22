package at.fhtw.swen3.paperless.team.mapper

import at.fhtw.swen3.paperless.dto.CreateTeamRequest
import at.fhtw.swen3.paperless.dto.TeamResponse
import at.fhtw.swen3.paperless.dto.UpdateTeamRequest
import at.fhtw.swen3.paperless.team.model.Team
import java.time.Instant
import java.time.ZoneOffset

object TeamMapper {

    fun toDto(team: Team): TeamResponse =
        TeamResponse(
            id = team.id,
            name = team.name,
            description = team.description,
            createdAt = team.createdAt.atOffset(ZoneOffset.UTC),
            updatedAt = team.updatedAt?.atOffset(ZoneOffset.UTC)
        )

    fun fromCreateDto(dto: CreateTeamRequest, now: Instant): Team =
        Team(
            id = 0,
            name = dto.name,
            description = dto.description,
            createdAt = now,
            updatedAt = null
        )

    fun applyUpdate(team: Team, dto: UpdateTeamRequest, now: Instant): Team =
        team.copy(
            name = dto.name ?: team.name,
            description = dto.description ?: team.description,
            updatedAt = now
        )
}
