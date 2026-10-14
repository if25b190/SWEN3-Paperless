package at.fhtw.swen3.paperless.auth.persistence

import at.fhtw.swen3.paperless.auth.model.AccessToken
import at.fhtw.swen3.paperless.auth.entity.AccessTokenEntity
import at.fhtw.swen3.paperless.auth.persistence.repository.AccessTokenRepository
import at.fhtw.swen3.paperless.user.entity.UserEntity
import jakarta.persistence.EntityManager
import java.util.Optional
import java.util.UUID
import org.springframework.stereotype.Repository

@Repository
class JpaAccessTokenPersistence(
    private val repository: AccessTokenRepository,
    private val entityManager: EntityManager
) : AccessTokenPersistence {

    override fun findById(digest: String): Optional<AccessToken> = repository.findById(digest).map(::toModel)

    override fun save(token: AccessToken): AccessToken = toModel(repository.save(
        AccessTokenEntity(
            token.digest, entityManager.getReference(UserEntity::class.java, token.userId), token.expiresAt
        )
    ))

    override fun deleteByUserId(userId: UUID): Int = repository.deleteByUserId(userId)

    private fun toModel(entity: AccessTokenEntity): AccessToken =
        AccessToken(entity.digest, requireNotNull(entity.user.id), entity.expiresAt)
}
