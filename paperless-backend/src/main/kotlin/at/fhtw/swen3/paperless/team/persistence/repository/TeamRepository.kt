package at.fhtw.swen3.paperless.team.persistence.repository

import at.fhtw.swen3.paperless.team.persistence.entity.TeamEntity
import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TeamRepository : JpaRepository<TeamEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select team from TeamEntity team where team.id = :id")
    fun findLockedById(@Param("id") id: UUID): TeamEntity?
}
