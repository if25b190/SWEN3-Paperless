package at.fhtw.swen3.paperless.team.service

import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.model.Team
import at.fhtw.swen3.paperless.team.model.TeamMember
import java.util.UUID

interface TeamService {

    fun getTeams(): List<Team>

    fun createTeam(team: Team): Team

    fun getTeam(id: UUID): Team

    fun updateTeam(id: UUID, name: String?, description: String?): Team

    fun deleteTeam(id: UUID)

    fun getTeamMembers(teamId: UUID): List<TeamMember>

    fun roleFor(teamId: UUID, userId: UUID): Role?

    fun visibleTeamIds(userId: UUID): Set<UUID>

    fun lockedRoles(teamIds: Set<UUID>, userId: UUID): Map<UUID, Role?>

    fun addTeamMember(teamId: UUID, userId: UUID, role: Role): TeamMember

    fun updateTeamMemberRole(teamId: UUID, userId: UUID, role: Role): TeamMember

    fun removeTeamMember(teamId: UUID, userId: UUID)
}
