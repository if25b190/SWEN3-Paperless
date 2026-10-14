package at.fhtw.swen3.paperless.user.persistence

import at.fhtw.swen3.paperless.user.model.User
import java.util.Optional
import java.util.UUID

interface UserPersistence {
    fun findAll(): List<User>
    fun findById(id: UUID): Optional<User>
    fun findByUsername(username: String): User?
    fun findLockedById(id: UUID): User?
    fun findLockedByUsername(username: String): User?
    fun save(user: User): User
    fun deleteById(id: UUID)
}
