package at.fhtw.swen3.paperless.team.persistence.mapper

import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.team.model.Team
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
interface TeamEntityMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = ["requireNotNull"])
    fun toModel(entity: TeamEntity): Team

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
}
