package at.fhtw.swen3.paperless.team.repository

import at.fhtw.swen3.paperless.team.entity.TeamMemberEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TeamMemberRepository : JpaRepository<TeamMemberEntity, UUID> {

    fun findByTeamId(teamId: UUID): List<TeamMemberEntity>

    fun findByUserId(userId: UUID): List<TeamMemberEntity>

    fun findByTeamIdAndUserId(teamId: UUID, userId: UUID): TeamMemberEntity?

    fun existsByTeamIdAndUserId(teamId: UUID, userId: UUID): Boolean

    fun deleteByTeamIdAndUserId(teamId: UUID, userId: UUID)

    fun deleteByTeamId(teamId: UUID)
}
