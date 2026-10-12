package at.fhtw.swen3.paperless.team.persistence.mapper

import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.team.persistence.entity.TeamEntity
import at.fhtw.swen3.paperless.team.persistence.entity.TeamMemberEntity
import at.fhtw.swen3.paperless.user.persistence.entity.UserEntity
import at.fhtw.swen3.paperless.user.persistence.mapper.UserEntityMapper

object TeamMemberEntityMapper {

    fun toEntity(member: TeamMember, team: TeamEntity, user: UserEntity): TeamMemberEntity =
        TeamMemberEntity(
            team = team,
            user = user,
            role = member.role,
            joinedAt = member.joinedAt
        )

    fun toModel(entity: TeamMemberEntity): TeamMember =
        TeamMember(
            team = TeamEntityMapper.toModel(entity.team),
            user = UserEntityMapper.toModel(entity.user),
            role = entity.role,
            joinedAt = entity.joinedAt
        )
}
