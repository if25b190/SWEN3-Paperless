package at.fhtw.swen3.paperless.team.controller

import at.fhtw.swen3.paperless.api.TeamsApi
import at.fhtw.swen3.paperless.dto.AddTeamMemberRequest
import at.fhtw.swen3.paperless.dto.CreateTeamRequest
import at.fhtw.swen3.paperless.dto.TeamListResponse
import at.fhtw.swen3.paperless.dto.TeamMemberListResponse
import at.fhtw.swen3.paperless.dto.TeamMemberResponse
import at.fhtw.swen3.paperless.dto.TeamResponse
import at.fhtw.swen3.paperless.dto.UpdateTeamMemberRoleRequest
import at.fhtw.swen3.paperless.dto.UpdateTeamRequest
import at.fhtw.swen3.paperless.team.mapper.TeamMapper
import at.fhtw.swen3.paperless.team.mapper.TeamMemberMapper
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.service.TeamService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.util.UUID
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class TeamController(private val teamService: TeamService) : TeamsApi {

    override fun getTeams(): ResponseEntity<TeamListResponse> {
        val teams = teamService.getTeams()
        val response = TeamListResponse(teams.map(TeamMapper::toDto))
        return ResponseEntity.ok(response)
    }

    override fun createTeam(createTeamRequest: CreateTeamRequest): ResponseEntity<TeamResponse> {
        val now = Instant.now()
        val team = TeamMapper.fromCreateDto(createTeamRequest, now)
        val created = teamService.createTeam(team)
        val response = TeamMapper.toDto(created)
        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.LOCATION, "/teams/${created.id}")
            .body(response)
    }

    override fun getTeamById(id: UUID): ResponseEntity<TeamResponse> {
        val team = teamService.getTeam(id)
        val response = TeamMapper.toDto(team)
        return ResponseEntity.ok(response)
    }

    override fun updateTeam(id: UUID, updateTeamRequest: UpdateTeamRequest): ResponseEntity<TeamResponse> {
        val updated = teamService.updateTeam(id, updateTeamRequest.name, updateTeamRequest.description)
        val response = TeamMapper.toDto(updated)
        return ResponseEntity.ok(response)
    }

    override fun deleteTeam(id: UUID): ResponseEntity<Unit> {
        teamService.deleteTeam(id)
        return ResponseEntity.noContent().build()
    }

    override fun getTeamMembers(id: UUID): ResponseEntity<TeamMemberListResponse> {
        val members = teamService.getTeamMembers(id)
        val response = TeamMemberListResponse(members.map(TeamMemberMapper::toMemberDto))
        return ResponseEntity.ok(response)
    }

    override fun addTeamMember(
        id: UUID,
        addTeamMemberRequest: AddTeamMemberRequest
    ): ResponseEntity<TeamMemberResponse> {
        val role = Role.valueOf(addTeamMemberRequest.role.name)
        val member = teamService.addTeamMember(id, addTeamMemberRequest.userId, role)
        val response = TeamMemberMapper.toMemberDto(member)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    override fun updateTeamMemberRole(
        id: UUID,
        userId: UUID,
        updateTeamMemberRoleRequest: UpdateTeamMemberRoleRequest
    ): ResponseEntity<TeamMemberResponse> {
        val role = Role.valueOf(updateTeamMemberRoleRequest.role.name)
        val member = teamService.updateTeamMemberRole(id, userId, role)
        val response = TeamMemberMapper.toMemberDto(member)
        return ResponseEntity.ok(response)
    }

    override fun removeTeamMember(id: UUID, userId: UUID): ResponseEntity<Unit> {
        teamService.removeTeamMember(id, userId)
        return ResponseEntity.noContent().build()
    }

}
