package at.fhtw.swen3.paperless.team.service

import at.fhtw.swen3.paperless.auth.model.AuthenticatedPrincipal
import at.fhtw.swen3.paperless.exception.TeamMemberAlreadyExistsException
import at.fhtw.swen3.paperless.exception.TeamMemberNotFoundException
import at.fhtw.swen3.paperless.exception.TeamNotFoundException
import at.fhtw.swen3.paperless.exception.UserNotFoundException
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.model.Team
import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.team.persistence.TeamMemberPersistence
import at.fhtw.swen3.paperless.team.persistence.TeamPersistence
import at.fhtw.swen3.paperless.user.persistence.UserPersistence
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class TeamServiceImpl(
    private val teamRepository: TeamPersistence,
    private val teamMemberRepository: TeamMemberPersistence,
    private val userRepository: UserPersistence
) : TeamService {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    override fun getTeams(): List<Team> =
        teamMemberRepository.findByUserId(currentActorId())
            .map { it.team }

    override fun createTeam(team: Team): Team {
        val actorId = currentActorId()
        val createdTeam = teamRepository.save(team.copy(ownerId = actorId))
        val creator = userRepository.findById(actorId).orElseThrow { UserNotFoundException() }
        val membership = TeamMember(createdTeam, creator, Role.ADMIN, Instant.now())
        teamMemberRepository.save(membership)
        log.info("[TEAM] - ACTION: createTeam: teamId: {}, actorId: {}", createdTeam.id, actorId)

        return createdTeam
    }

    @Transactional(readOnly = true)
    override fun getTeam(id: UUID): Team {
        val entity = teamRepository.findById(id)
            .orElseThrow { TeamNotFoundException() }
        requireTeamMember(entity)
        return entity
    }

    override fun updateTeam(id: UUID, name: String?, description: String?): Team {
        val entity = teamRepository.findById(id)
            .orElseThrow { TeamNotFoundException() }
        requireAdmin(id, entity.ownerId)
        val updated = entity.copy(
            name = name ?: entity.name,
            description = description ?: entity.description,
            updatedAt = Instant.now()
        )

        val saved = teamRepository.save(updated)
        log.info("[TEAM] - ACTION: updateTeam: teamId: {}", id)

        return saved
    }

    override fun deleteTeam(id: UUID) {
        val team = findLockedTeam(id)
        if (currentActorId() != team.ownerId) {
            throw AccessDeniedException("Only the team owner can delete this team")
        }
        teamMemberRepository.deleteByTeamId(id)
        teamRepository.deleteById(id)
        log.info("[TEAM] - ACTION: deleteTeam: teamId: {}", id)
    }

    @Transactional(readOnly = true)
    override fun getTeamMembers(teamId: UUID): List<TeamMember> {
        getTeam(teamId)
        return teamMemberRepository.findByTeamId(teamId)
    }

    override fun addTeamMember(teamId: UUID, userId: UUID, role: Role): TeamMember {
        val team = findLockedTeam(teamId)
        requireAdmin(teamId, team.ownerId)
        if (userId == team.ownerId && role != Role.ADMIN) {
            throw AccessDeniedException("The team owner must remain an admin")
        }
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException() }
        if (teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw TeamMemberAlreadyExistsException()
        }

        val member = TeamMember(team, user, role, Instant.now())
        teamMemberRepository.save(member)
        log.info("[TEAM] - ACTION: addTeamMember: teamId: {}, userId: {}, role: {}", teamId, userId, role)

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
        val saved = teamMemberRepository.save(member.copy(role = role))
        log.info("[TEAM] - ACTION: updateTeamMemberRole: teamId: {}, userId: {}, role: {}", teamId, userId, role)

        return saved
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
        log.info("[TEAM] - ACTION: removeTeamMember: teamId: {}, userId: {}", teamId, userId)
    }

    private fun findLockedTeam(teamId: UUID) =
        teamRepository.findLockedById(teamId)
            ?: throw TeamNotFoundException()

    private fun requireAnotherAdmin(teamId: UUID, member: TeamMember) {
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

    private fun requireTeamMember(team: Team) {
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
            ?: throw TeamMemberNotFoundException()
}
