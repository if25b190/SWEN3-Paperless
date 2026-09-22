package at.fhtw.swen3.paperless.user.service

import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.team.mapper.TeamMemberEntityMapper
import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.team.repository.TeamMemberRepository
import at.fhtw.swen3.paperless.user.mapper.UserEntityMapper
import at.fhtw.swen3.paperless.user.model.User
import at.fhtw.swen3.paperless.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val teamMemberRepository: TeamMemberRepository
) : UserService {

    @Transactional(readOnly = true)
    override fun searchUsers(): List<User> =
        userRepository.findAll().map(UserEntityMapper::toModel)

    override fun create(user: User): User {
        ensureUsernameAvailable(user.username)
        ensureEmailAvailable(user.email)

        val entity = UserEntityMapper.toEntity(user)
        val saved = userRepository.save(entity)

        return UserEntityMapper.toModel(saved)
    }

    @Transactional(readOnly = true)
    override fun getById(id: Long): User =
        userRepository.findById(id)
            .map(UserEntityMapper::toModel)
            .orElseThrow { AppException(AppErrorMessage.USER_NOT_FOUND) }

    override fun update(user: User): User {
        val existing = userRepository.findById(user.id)
            .orElseThrow { AppException(AppErrorMessage.USER_NOT_FOUND) }

        if (user.username != existing.username) {
            ensureUsernameAvailable(user.username, user.id)
        }

        if (user.email != existing.email) {
            ensureEmailAvailable(user.email, user.id)
        }

        val updated = user.copy(updatedAt = Instant.now())
        val saved = userRepository.save(UserEntityMapper.toEntity(updated))

        return UserEntityMapper.toModel(saved)
    }

    override fun delete(id: Long) {
        userRepository.findById(id)
            .orElseThrow { AppException(AppErrorMessage.USER_NOT_FOUND) }

        userRepository.deleteById(id)
    }

    @Transactional(readOnly = true)
    override fun getTeams(id: Long): List<TeamMember> {
        getById(id)

        return teamMemberRepository.findByUserId(id).map(TeamMemberEntityMapper::toModel)
    }

    @Transactional(readOnly = true)
    override fun authenticate(username: String, password: String): User {
        val user = userRepository.findByUsername(username)
            ?: throw AppException(AppErrorMessage.INVALID_CREDENTIALS)

        if (user.password != password) {
            throw AppException(AppErrorMessage.INVALID_CREDENTIALS)
        }

        return UserEntityMapper.toModel(user)
    }

    private fun ensureUsernameAvailable(username: String, ignoredId: Long? = null) {
        val duplicate = userRepository.findByUsername(username)
        if (duplicate != null && duplicate.id != ignoredId) {
            throw AppException(AppErrorMessage.USERNAME_ALREADY_EXISTS)
        }
    }

    private fun ensureEmailAvailable(email: String, ignoredId: Long? = null) {
        val duplicate = userRepository.findAll().firstOrNull { it.email == email }
        if (duplicate != null && duplicate.id != ignoredId) {
            throw AppException(AppErrorMessage.EMAIL_ALREADY_EXISTS)
        }
    }
}
