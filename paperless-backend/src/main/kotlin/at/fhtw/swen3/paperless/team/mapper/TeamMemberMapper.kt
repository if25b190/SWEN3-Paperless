package at.fhtw.swen3.paperless.team.mapper

import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.dto.TeamMemberResponse
import at.fhtw.swen3.paperless.dto.UserTeamMembershipResponse
import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.user.mapper.UserMapper
import org.mapstruct.InjectionStrategy
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingConstants
import org.mapstruct.ReportingPolicy

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = [MappingConverters::class, UserMapper::class, TeamMapper::class],
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface TeamMemberMapper {

    @Mapping(target = "joinedAt", source = "joinedAt", qualifiedByName = ["toOffset"])
    fun toMemberDto(member: TeamMember): TeamMemberResponse

    fun toMembershipDto(member: TeamMember): UserTeamMembershipResponse
}
