package at.fhtw.swen3.paperless.auth.persistence

import at.fhtw.swen3.paperless.auth.model.AccessToken
import java.util.Optional
import java.util.UUID

interface AccessTokenPersistence {
    fun findById(digest: String): Optional<AccessToken>
    fun save(token: AccessToken): AccessToken
    fun deleteByUserId(userId: UUID): Int
}
