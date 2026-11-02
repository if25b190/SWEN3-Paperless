package at.fhtw.swen3.paperless.team.controller

import at.fhtw.swen3.paperless.api.TeamsApi
import at.fhtw.swen3.paperless.auth.model.AuthenticatedPrincipal
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
import org.springframework.security.core.Authentication
import java.util.UUID
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class TeamController(
    private val teamService: TeamService,
    private val teamMapper: TeamMapper,
    private val teamMemberMapper: TeamMemberMapper
) : TeamsApi {

    override fun getTeams(authentication: Authentication?): ResponseEntity<TeamListResponse> {
        val actorId = AuthenticatedPrincipal.userId(authentication)
        val teams = teamService.getTeams(actorId)
        val response = TeamListResponse(teams.map(teamMapper::toDto))
        return ResponseEntity.ok(response)
    }

    override fun createTeam(createTeamRequest: CreateTeamRequest, authentication: Authentication?): ResponseEntity<TeamResponse> {
        val actorId = AuthenticatedPrincipal.userId(authentication)
        val now = Instant.now()
        val team = teamMapper.fromCreateDto(createTeamRequest, now)
        val created = teamService.createTeam(actorId, team)
        val response = teamMapper.toDto(created)
        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.LOCATION, "/api/v1/teams/${created.id}")
            .body(response)
    }

    override fun getTeamById(id: UUID, authentication: Authentication?): ResponseEntity<TeamResponse> {
        val actorId = AuthenticatedPrincipal.userId(authentication)
        val team = teamService.getTeam(actorId, id)
        val response = teamMapper.toDto(team)
        return ResponseEntity.ok(response)
    }

    override fun updateTeam(id: UUID, updateTeamRequest: UpdateTeamRequest, authentication: Authentication?): ResponseEntity<TeamResponse> {
        val actorId = AuthenticatedPrincipal.userId(authentication)
        val updated = teamService.updateTeam(actorId, id, updateTeamRequest.name, updateTeamRequest.description)
        val response = teamMapper.toDto(updated)
        return ResponseEntity.ok(response)
    }

    override fun deleteTeam(id: UUID, authentication: Authentication?): ResponseEntity<Unit> {
        teamService.deleteTeam(AuthenticatedPrincipal.userId(authentication), id)
        return ResponseEntity.noContent().build()
    }

    override fun getTeamMembers(id: UUID, authentication: Authentication?): ResponseEntity<TeamMemberListResponse> {
        val actorId = AuthenticatedPrincipal.userId(authentication)
        val members = teamService.getTeamMembers(actorId, id)
        val response = TeamMemberListResponse(members.map(teamMemberMapper::toMemberDto))
        return ResponseEntity.ok(response)
    }

    override fun addTeamMember(
        id: UUID,
        addTeamMemberRequest: AddTeamMemberRequest,
        authentication: Authentication?
    ): ResponseEntity<TeamMemberResponse> {
        val actorId = AuthenticatedPrincipal.userId(authentication)
        val role = Role.valueOf(addTeamMemberRequest.role.name)
        val member = teamService.addTeamMember(actorId, id, addTeamMemberRequest.userId, role)
        val response = teamMemberMapper.toMemberDto(member)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    override fun updateTeamMemberRole(
        id: UUID,
        userId: UUID,
        updateTeamMemberRoleRequest: UpdateTeamMemberRoleRequest,
        authentication: Authentication?
    ): ResponseEntity<TeamMemberResponse> {
        val actorId = AuthenticatedPrincipal.userId(authentication)
        val role = Role.valueOf(updateTeamMemberRoleRequest.role.name)
        val member = teamService.updateTeamMemberRole(actorId, id, userId, role)
        val response = teamMemberMapper.toMemberDto(member)
        return ResponseEntity.ok(response)
    }

    override fun removeTeamMember(id: UUID, userId: UUID, authentication: Authentication?): ResponseEntity<Unit> {
        teamService.removeTeamMember(AuthenticatedPrincipal.userId(authentication), id, userId)
        return ResponseEntity.noContent().build()
    }

}
