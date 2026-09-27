package at.fhtw.swen3.paperless.auth.repository

import at.fhtw.swen3.paperless.auth.entity.AccessTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface AccessTokenRepository : JpaRepository<AccessTokenEntity, String> {
    @Modifying
    @Query("delete from AccessTokenEntity t where t.user.id = :userId")
    fun deleteByUserId(@Param("userId") userId: UUID): Int

    @Query("select count(t) from AccessTokenEntity t where t.user.id = :userId")
    fun countByUserId(@Param("userId") userId: UUID): Long
}
