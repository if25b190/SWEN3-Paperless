package at.fhtw.swen3.paperless.user.persistence

import at.fhtw.swen3.paperless.user.model.User
import at.fhtw.swen3.paperless.user.persistence.mapper.UserEntityMapper
import at.fhtw.swen3.paperless.user.persistence.repository.UserRepository
import java.util.Optional
import java.util.UUID
import org.springframework.stereotype.Repository
import jakarta.persistence.EntityManager

@Repository
class JpaUserPersistence(
    private val repository: UserRepository,
    private val userEntityMapper: UserEntityMapper,
    private val entityManager: EntityManager,
) : UserPersistence {

    override fun findAll(): List<User> = repository.findAll().map(userEntityMapper::toModel)

    override fun findById(id: UUID): Optional<User> = repository.findById(id).map(userEntityMapper::toModel)

    override fun findByUsername(username: String): User? =
        repository.findByUsername(username)?.let(userEntityMapper::toModel)

    override fun findLockedById(id: UUID): User? = repository.findLockedById(id)?.let(userEntityMapper::toModel)

    override fun findLockedByUsername(username: String): User? =
        repository.findLockedByUsername(username)?.let(userEntityMapper::toModel)

    override fun save(user: User): User = userEntityMapper.toModel(repository.save(userEntityMapper.toEntity(user)))

    override fun update(id: UUID, update: User): User {
        val entity = repository.findLockedById(id) ?: throw NoSuchElementException("User $id not found")
        userEntityMapper.applyTo(entity, update)
        entityManager.flush()

        return userEntityMapper.toModel(entity)
    }

    override fun deleteById(id: UUID) = repository.deleteById(id)
}
