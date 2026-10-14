package at.fhtw.swen3.paperless.team.persistence

import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.team.persistence.mapper.TeamMemberEntityMapper
import at.fhtw.swen3.paperless.team.persistence.repository.TeamMemberRepository
import at.fhtw.swen3.paperless.user.entity.UserEntity
import jakarta.persistence.EntityManager
import java.util.UUID
import org.springframework.stereotype.Repository

@Repository
class JpaTeamMemberPersistence(
    private val repository: TeamMemberRepository,
    private val entityManager: EntityManager
) : TeamMemberPersistence {

    override fun findByTeamId(teamId: UUID): List<TeamMember> =
        repository.findByTeamId(teamId).map(TeamMemberEntityMapper::toModel)

    override fun findByUserId(userId: UUID): List<TeamMember> =
        repository.findByUserId(userId).map(TeamMemberEntityMapper::toModel)

    override fun findByTeamIdAndUserId(teamId: UUID, userId: UUID): TeamMember? =
        repository.findByTeamIdAndUserId(teamId, userId)?.let(TeamMemberEntityMapper::toModel)

    override fun existsByTeamIdAndUserId(teamId: UUID, userId: UUID): Boolean =
        repository.existsByTeamIdAndUserId(teamId, userId)

    override fun save(member: TeamMember): TeamMember {
        val existing = repository.findByTeamIdAndUserId(requireNotNull(member.team.id), requireNotNull(member.user.id))
        val entity = existing?.also { it.role = member.role } ?: TeamMemberEntityMapper.toEntity(
            member,
            entityManager.getReference(TeamEntity::class.java, requireNotNull(member.team.id)),
            entityManager.getReference(UserEntity::class.java, requireNotNull(member.user.id))
        )

        return TeamMemberEntityMapper.toModel(repository.save(entity))
    }

    override fun delete(member: TeamMember) =
        deleteByTeamIdAndUserId(requireNotNull(member.team.id), requireNotNull(member.user.id))

    override fun deleteAll(members: List<TeamMember>) {
        members.forEach { deleteByTeamIdAndUserId(requireNotNull(it.team.id), requireNotNull(it.user.id)) }
    }

    override fun deleteByTeamId(teamId: UUID) = repository.deleteByTeamId(teamId)

    override fun deleteByTeamIdAndUserId(teamId: UUID, userId: UUID) =
        repository.deleteByTeamIdAndUserId(teamId, userId)
}
