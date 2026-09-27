package at.fhtw.swen3.paperless.team.repository

import at.fhtw.swen3.paperless.team.entity.TeamEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TeamRepository : JpaRepository<TeamEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select team from TeamEntity team where team.id = :id")
    fun findLockedById(@Param("id") id: UUID): TeamEntity?
}
