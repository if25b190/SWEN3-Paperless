package at.fhtw.swen3.paperless.user.persistence

import at.fhtw.swen3.paperless.user.model.User
import at.fhtw.swen3.paperless.user.persistence.mapper.UserEntityMapper
import at.fhtw.swen3.paperless.user.persistence.repository.UserRepository
import java.util.Optional
import java.util.UUID
import org.springframework.stereotype.Repository

@Repository
class JpaUserPersistence(private val repository: UserRepository) : UserPersistence {

    override fun findAll(): List<User> = repository.findAll().map(UserEntityMapper::toModel)

    override fun findById(id: UUID): Optional<User> = repository.findById(id).map(UserEntityMapper::toModel)

    override fun findByUsername(username: String): User? =
        repository.findByUsername(username)?.let(UserEntityMapper::toModel)

    override fun findLockedById(id: UUID): User? = repository.findLockedById(id)?.let(UserEntityMapper::toModel)

    override fun findLockedByUsername(username: String): User? =
        repository.findLockedByUsername(username)?.let(UserEntityMapper::toModel)

    override fun save(user: User): User = UserEntityMapper.toModel(repository.save(UserEntityMapper.toEntity(user)))

    override fun deleteById(id: UUID) = repository.deleteById(id)
}
