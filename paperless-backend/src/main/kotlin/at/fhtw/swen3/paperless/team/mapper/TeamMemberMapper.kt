package at.fhtw.swen3.paperless.team.mapper

import at.fhtw.swen3.paperless.dto.Role as DtoRole
import at.fhtw.swen3.paperless.dto.TeamMemberResponse
import at.fhtw.swen3.paperless.dto.UserTeamMembershipResponse
import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.user.mapper.UserMapper
import java.time.ZoneOffset

object TeamMemberMapper {

    fun toMemberDto(member: TeamMember): TeamMemberResponse =
        TeamMemberResponse(
            user = UserMapper.toDto(member.user),
            role = DtoRole.valueOf(member.role.name),
            joinedAt = member.joinedAt?.atOffset(ZoneOffset.UTC)
        )

    fun toMembershipDto(member: TeamMember): UserTeamMembershipResponse =
        UserTeamMembershipResponse(
            team = TeamMapper.toDto(member.team),
            role = DtoRole.valueOf(member.role.name)
        )
}
