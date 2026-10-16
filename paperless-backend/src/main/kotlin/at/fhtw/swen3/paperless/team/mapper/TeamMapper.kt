package at.fhtw.swen3.paperless.team.mapper

import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.dto.CreateTeamRequest
import at.fhtw.swen3.paperless.dto.TeamResponse
import at.fhtw.swen3.paperless.team.model.Team
import java.time.Instant
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
interface TeamMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = ["requireNotNull"])
    @Mapping(target = "ownerId", source = "ownerId", qualifiedByName = ["requireNotNull"])
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = ["toOffset"])
    @Mapping(target = "updatedAt", source = "updatedAt", qualifiedByName = ["toOffset"])
    fun toDto(team: Team): TeamResponse

    fun fromCreateDto(dto: CreateTeamRequest, now: Instant): Team =
        Team(
            id = null,
            name = dto.name,
            description = dto.description,
            createdAt = now,
            updatedAt = null,
            ownerId = null
        )
}
