package at.fhtw.swen3.paperless.team.service

import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.team.mapper.TeamEntityMapper
import at.fhtw.swen3.paperless.team.mapper.TeamMemberEntityMapper
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.model.Team
import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.team.repository.TeamMemberRepository
import at.fhtw.swen3.paperless.team.repository.TeamRepository
import at.fhtw.swen3.paperless.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class TeamServiceImpl(
    private val teamRepository: TeamRepository,
    private val teamMemberRepository: TeamMemberRepository,
    private val userService: UserService
) : TeamService {

    @Transactional(readOnly = true)
    override fun getTeams(): List<Team> =
        teamRepository.findAll().map(TeamEntityMapper::toModel)

    override fun createTeam(team: Team): Team {
        val saved = teamRepository.save(TeamEntityMapper.toEntity(team))
        return TeamEntityMapper.toModel(saved)
    }

    @Transactional(readOnly = true)
    override fun getTeam(id: Long): Team =
        teamRepository.findById(id).map(TeamEntityMapper::toModel)
            .orElseThrow { AppException(AppErrorMessage.TEAM_NOT_FOUND) }

    override fun updateTeam(id: Long, name: String?, description: String?): Team {
        val entity = teamRepository.findById(id)
            .orElseThrow { AppException(AppErrorMessage.TEAM_NOT_FOUND) }
        entity.name = name ?: entity.name
        entity.description = description ?: entity.description
        entity.updatedAt = Instant.now()
        return TeamEntityMapper.toModel(teamRepository.save(entity))
    }

    override fun deleteTeam(id: Long) {
        getTeam(id)
        teamMemberRepository.deleteByTeamId(id)
        teamRepository.deleteById(id)
    }

    @Transactional(readOnly = true)
    override fun getTeamMembers(teamId: Long): List<TeamMember> {
        getTeam(teamId)
        return teamMemberRepository.findByTeamId(teamId).map(TeamMemberEntityMapper::toModel)
    }

    override fun addTeamMember(teamId: Long, userId: Long, role: Role): TeamMember {
        val team = getTeam(teamId)
        val user = userService.getById(userId)
        if (teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw AppException(AppErrorMessage.TEAM_MEMBER_ALREADY_EXISTS)
        }

        val member = TeamMember(team, user, role, Instant.now())
        teamMemberRepository.save(TeamMemberEntityMapper.toEntity(member))
        return member
    }

    override fun updateTeamMemberRole(teamId: Long, userId: Long, role: Role): TeamMember {
        getTeam(teamId)
        val member = findMember(teamId, userId)
        member.role = role
        return TeamMemberEntityMapper.toModel(teamMemberRepository.save(member))
    }

    override fun removeTeamMember(teamId: Long, userId: Long) {
        getTeam(teamId)
        teamMemberRepository.delete(findMember(teamId, userId))
    }

    private fun findMember(teamId: Long, userId: Long) =
        teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
            ?: throw AppException(AppErrorMessage.TEAM_MEMBER_NOT_FOUND)
}
