package at.fhtw.swen3.paperless.team.persistence

import at.fhtw.swen3.paperless.team.model.TeamMember
import java.util.UUID

interface TeamMemberPersistence {
    fun findByTeamId(teamId: UUID): List<TeamMember>
    fun findByUserId(userId: UUID): List<TeamMember>
    fun findByTeamIdAndUserId(teamId: UUID, userId: UUID): TeamMember?
    fun existsByTeamIdAndUserId(teamId: UUID, userId: UUID): Boolean
    fun save(member: TeamMember): TeamMember
    fun delete(member: TeamMember)
    fun deleteAll(members: List<TeamMember>)
    fun deleteByTeamIdAndUserId(teamId: UUID, userId: UUID)
}
