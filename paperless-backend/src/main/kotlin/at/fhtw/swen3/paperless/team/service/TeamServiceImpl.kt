package at.fhtw.swen3.paperless.team.service

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
    override fun getTeams(actorId: UUID): List<Team> =
        teamMemberRepository.findByUserId(actorId)
            .map { it.team }

    override fun createTeam(actorId: UUID, team: Team): Team {
        val createdTeam = teamRepository.save(team.copy(ownerId = actorId))
        val creator = userRepository.findById(actorId).orElseThrow { UserNotFoundException() }
        val membership = TeamMember(createdTeam, creator, Role.ADMIN, Instant.now())
        teamMemberRepository.save(membership)
        log.info("[TEAM] - ACTION: createTeam: teamId: {}, actorId: {}", createdTeam.id, actorId)

        return createdTeam
    }

    @Transactional(readOnly = true)
    override fun getTeam(actorId: UUID, id: UUID): Team {
        val entity = teamRepository.findById(id)
            .orElseThrow { TeamNotFoundException() }
        requireTeamMember(actorId, entity)
        return entity
    }

    override fun updateTeam(actorId: UUID, id: UUID, name: String?, description: String?): Team {
        val entity = teamRepository.findById(id)
            .orElseThrow { TeamNotFoundException() }
        requireAdmin(actorId, id, entity.ownerId)
        val updated = entity.copy(
            name = name ?: entity.name,
            description = description ?: entity.description,
            updatedAt = Instant.now()
        )

        val saved = teamRepository.save(updated)
        log.info("[TEAM] - ACTION: updateTeam: teamId: {}", id)

        return saved
    }

    override fun deleteTeam(actorId: UUID, id: UUID) {
        val team = findLockedTeam(id)
        if (actorId != team.ownerId) {
            throw AccessDeniedException("Only the team owner can delete this team")
        }
        teamRepository.deleteById(id)
        log.info("[TEAM] - ACTION: deleteTeam: teamId: {}", id)
    }

    @Transactional(readOnly = true)
    override fun getTeamMembers(actorId: UUID, teamId: UUID): List<TeamMember> {
        getTeam(actorId, teamId)
        return teamMemberRepository.findByTeamId(teamId)
    }

    override fun addTeamMember(actorId: UUID, teamId: UUID, userId: UUID, role: Role): TeamMember {
        val team = findLockedTeam(teamId)
        requireAdmin(actorId, teamId, team.ownerId)
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

    override fun updateTeamMemberRole(actorId: UUID, teamId: UUID, userId: UUID, role: Role): TeamMember {
        val team = findLockedTeam(teamId)
        requireAdmin(actorId, teamId, team.ownerId)
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

    override fun removeTeamMember(actorId: UUID, teamId: UUID, userId: UUID) {
        val team = findLockedTeam(teamId)
        requireAdmin(actorId, teamId, team.ownerId)
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

    private fun requireAdmin(actorId: UUID, teamId: UUID, ownerId: UUID?) {
        val isAdmin = actorId == ownerId ||
            teamMemberRepository.findByTeamIdAndUserId(teamId, actorId)?.role == Role.ADMIN
        if (!isAdmin) {
            throw AccessDeniedException("Admin membership required for this team")
        }
    }

    private fun requireTeamMember(actorId: UUID, team: Team) {
        val isMember = actorId == team.ownerId || teamMemberRepository.existsByTeamIdAndUserId(requireNotNull(team.id), actorId)
        if (!isMember) {
            throw AccessDeniedException("Team membership required to read this team")
        }
    }

    private fun findMember(teamId: UUID, userId: UUID) =
        teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
            ?: throw TeamMemberNotFoundException()
}
