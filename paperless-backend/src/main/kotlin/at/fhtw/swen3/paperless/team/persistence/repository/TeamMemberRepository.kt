package at.fhtw.swen3.paperless.team.persistence.repository

import at.fhtw.swen3.paperless.team.entity.TeamMemberEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface TeamMemberRepository : JpaRepository<TeamMemberEntity, UUID> {

    fun findByTeamId(teamId: UUID): List<TeamMemberEntity>

    fun findByUserId(userId: UUID): List<TeamMemberEntity>

    fun findByTeamIdAndUserId(teamId: UUID, userId: UUID): TeamMemberEntity?

    fun existsByTeamIdAndUserId(teamId: UUID, userId: UUID): Boolean

    fun deleteByTeamIdAndUserId(teamId: UUID, userId: UUID)
}
