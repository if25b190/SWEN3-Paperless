package at.fhtw.swen3.paperless.team.service

import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.model.Team
import at.fhtw.swen3.paperless.team.model.TeamMember

interface TeamService {

    fun getTeams(): List<Team>

    fun createTeam(team: Team): Team

    fun getTeam(id: Long): Team

    fun updateTeam(id: Long, name: String?, description: String?): Team

    fun deleteTeam(id: Long)

    fun getTeamMembers(teamId: Long): List<TeamMember>

    fun addTeamMember(teamId: Long, userId: Long, role: Role): TeamMember

    fun updateTeamMemberRole(teamId: Long, userId: Long, role: Role): TeamMember

    fun removeTeamMember(teamId: Long, userId: Long)
}
