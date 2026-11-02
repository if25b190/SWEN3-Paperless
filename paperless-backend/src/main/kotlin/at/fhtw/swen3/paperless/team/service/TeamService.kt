package at.fhtw.swen3.paperless.team.service

import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.model.Team
import at.fhtw.swen3.paperless.team.model.TeamMember
import java.util.UUID

interface TeamService {

    fun getTeams(actorId: UUID): List<Team>

    fun createTeam(actorId: UUID, team: Team): Team

    fun getTeam(actorId: UUID, id: UUID): Team

    fun updateTeam(actorId: UUID, id: UUID, name: String?, description: String?): Team

    fun deleteTeam(actorId: UUID, id: UUID)

    fun getTeamMembers(actorId: UUID, teamId: UUID): List<TeamMember>

    fun addTeamMember(actorId: UUID, teamId: UUID, userId: UUID, role: Role): TeamMember

    fun updateTeamMemberRole(actorId: UUID, teamId: UUID, userId: UUID, role: Role): TeamMember

    fun removeTeamMember(actorId: UUID, teamId: UUID, userId: UUID)
}
