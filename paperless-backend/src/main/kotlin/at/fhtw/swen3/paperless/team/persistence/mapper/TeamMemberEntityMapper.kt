package at.fhtw.swen3.paperless.team.persistence.mapper

import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.team.entity.TeamMemberEntity
import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.user.entity.UserEntity
import at.fhtw.swen3.paperless.user.persistence.mapper.UserEntityMapper
import org.mapstruct.InjectionStrategy
import org.mapstruct.Mapper
import org.mapstruct.MappingConstants
import org.mapstruct.ReportingPolicy

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = [MappingConverters::class, TeamEntityMapper::class, UserEntityMapper::class],
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface TeamMemberEntityMapper {

    fun toModel(entity: TeamMemberEntity): TeamMember

    fun toEntity(member: TeamMember, team: TeamEntity, user: UserEntity): TeamMemberEntity =
        TeamMemberEntity(
            team = team,
            user = user,
            role = member.role,
            joinedAt = member.joinedAt
        )
}
