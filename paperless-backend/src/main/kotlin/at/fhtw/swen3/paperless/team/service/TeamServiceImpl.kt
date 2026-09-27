package at.fhtw.swen3.paperless.team.service

import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.auth.model.AuthenticatedPrincipal
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.team.entity.TeamMemberEntity
import at.fhtw.swen3.paperless.team.mapper.TeamEntityMapper
import at.fhtw.swen3.paperless.team.mapper.TeamMemberEntityMapper
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.model.Team
import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.team.repository.TeamMemberRepository
import at.fhtw.swen3.paperless.team.repository.TeamRepository
import at.fhtw.swen3.paperless.user.service.UserService
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class TeamServiceImpl(
    private val teamRepository: TeamRepository,
    private val teamMemberRepository: TeamMemberRepository,
    private val userService: UserService
) : TeamService {

    @Transactional(readOnly = true)
    override fun getTeams(): List<Team> =
        teamMemberRepository.findByUserId(currentActorId())
            .map { TeamEntityMapper.toModel(it.team) }

    override fun createTeam(team: Team): Team {
        val actorId = currentActorId()
        val saved = teamRepository.save(TeamEntityMapper.toEntity(team.copy(ownerId = actorId)))
        val createdTeam = TeamEntityMapper.toModel(saved)
        val creator = userService.getById(actorId)
        val membership = TeamMember(createdTeam, creator, Role.ADMIN, Instant.now())
        teamMemberRepository.save(TeamMemberEntityMapper.toEntity(membership))
        return createdTeam
    }

    @Transactional(readOnly = true)
    override fun getTeam(id: UUID): Team {
        val entity = teamRepository.findById(id)
            .orElseThrow { AppException(AppErrorMessage.TEAM_NOT_FOUND) }
        requireTeamMember(entity)
        return TeamEntityMapper.toModel(entity)
    }

    override fun updateTeam(id: UUID, name: String?, description: String?): Team {
        val entity = teamRepository.findById(id)
            .orElseThrow { AppException(AppErrorMessage.TEAM_NOT_FOUND) }
        requireAdmin(id, entity.ownerId)
        entity.name = name ?: entity.name
        entity.description = description ?: entity.description
        entity.updatedAt = Instant.now()
        return TeamEntityMapper.toModel(teamRepository.save(entity))
    }

    override fun deleteTeam(id: UUID) {
        val team = findLockedTeam(id)
        if (currentActorId() != team.ownerId) {
            throw AccessDeniedException("Only the team owner can delete this team")
        }
        teamMemberRepository.deleteByTeamId(id)
        teamRepository.deleteById(id)
    }

    @Transactional(readOnly = true)
    override fun getTeamMembers(teamId: UUID): List<TeamMember> {
        getTeam(teamId)
        return teamMemberRepository.findByTeamId(teamId).map(TeamMemberEntityMapper::toModel)
    }

    @Transactional(readOnly = true)
    override fun roleFor(teamId: UUID, userId: UUID): Role? =
        teamMemberRepository.findByTeamIdAndUserId(teamId, userId)?.role

    @Transactional(readOnly = true)
    override fun visibleTeamIds(userId: UUID): Set<UUID> =
        teamMemberRepository.findByUserId(userId).mapTo(mutableSetOf()) { requireNotNull(it.team.id) }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun lockedRoles(teamIds: Set<UUID>, userId: UUID): Map<UUID, Role?> {
        val teams = teamIds.sorted().map(::findLockedTeam)
        return teams.associate { team ->
            requireNotNull(team.id) to teamMemberRepository.findByTeamIdAndUserId(requireNotNull(team.id), userId)?.role
        }
    }

    override fun addTeamMember(teamId: UUID, userId: UUID, role: Role): TeamMember {
        val teamEntity = findLockedTeam(teamId)
        requireAdmin(teamId, teamEntity.ownerId)
        if (userId == teamEntity.ownerId && role != Role.ADMIN) {
            throw AccessDeniedException("The team owner must remain an admin")
        }
        val user = userService.getById(userId)
        if (teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw AppException(AppErrorMessage.TEAM_MEMBER_ALREADY_EXISTS)
        }

        val member = TeamMember(TeamEntityMapper.toModel(teamEntity), user, role, Instant.now())
        teamMemberRepository.save(TeamMemberEntityMapper.toEntity(member))
        return member
    }

    override fun updateTeamMemberRole(teamId: UUID, userId: UUID, role: Role): TeamMember {
        val team = findLockedTeam(teamId)
        requireAdmin(teamId, team.ownerId)
        if (userId == team.ownerId && role != Role.ADMIN) {
            throw AccessDeniedException("The team owner must remain an admin")
        }
        val member = findMember(teamId, userId)
        if (member.role == Role.ADMIN && role != Role.ADMIN) {
            requireAnotherAdmin(teamId, member)
        }
        member.role = role
        return TeamMemberEntityMapper.toModel(teamMemberRepository.save(member))
    }

    override fun removeTeamMember(teamId: UUID, userId: UUID) {
        val team = findLockedTeam(teamId)
        requireAdmin(teamId, team.ownerId)
        if (userId == team.ownerId) {
            throw AccessDeniedException("The team owner cannot be removed")
        }
        val member = findMember(teamId, userId)
        if (member.role == Role.ADMIN) {
            requireAnotherAdmin(teamId, member)
        }
        teamMemberRepository.delete(member)
    }

    private fun findLockedTeam(teamId: UUID) =
        teamRepository.findLockedById(teamId)
            ?: throw AppException(AppErrorMessage.TEAM_NOT_FOUND)

    private fun requireAnotherAdmin(teamId: UUID, member: TeamMemberEntity) {
        val anotherAdminExists = teamMemberRepository.findByTeamId(teamId).any {
            it.user.id != member.user.id && it.role == Role.ADMIN
        }
        if (!anotherAdminExists) {
            throw AccessDeniedException("A team must have at least one admin")
        }
    }

    private fun requireAdmin(teamId: UUID, ownerId: UUID?) {
        val actorId = currentActorId()
        val isAdmin = actorId == ownerId ||
            teamMemberRepository.findByTeamIdAndUserId(teamId, actorId)?.role == Role.ADMIN
        if (!isAdmin) {
            throw AccessDeniedException("Admin membership required for this team")
        }
    }

    private fun requireTeamMember(team: TeamEntity) {
        val actorId = currentActorId()
        val isMember = actorId == team.ownerId || teamMemberRepository.existsByTeamIdAndUserId(requireNotNull(team.id), actorId)
        if (!isMember) {
            throw AccessDeniedException("Team membership required to read this team")
        }
    }

    private fun currentActorId(): UUID =
        AuthenticatedPrincipal.userId(SecurityContextHolder.getContext().authentication?.name)

    private fun findMember(teamId: UUID, userId: UUID) =
        teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
            ?: throw AppException(AppErrorMessage.TEAM_MEMBER_NOT_FOUND)
}
