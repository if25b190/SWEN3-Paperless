package at.fhtw.swen3.paperless.team.repository

import at.fhtw.swen3.paperless.team.entity.TeamMemberEntity
import org.springframework.data.jpa.repository.JpaRepository

interface TeamMemberRepository : JpaRepository<TeamMemberEntity, Long> {

    fun findByTeamId(teamId: Long): List<TeamMemberEntity>

    fun findByUserId(userId: Long): List<TeamMemberEntity>

    fun findByTeamIdAndUserId(teamId: Long, userId: Long): TeamMemberEntity?

    fun existsByTeamIdAndUserId(teamId: Long, userId: Long): Boolean

    fun deleteByTeamIdAndUserId(teamId: Long, userId: Long)

    fun deleteByTeamId(teamId: Long)
}
