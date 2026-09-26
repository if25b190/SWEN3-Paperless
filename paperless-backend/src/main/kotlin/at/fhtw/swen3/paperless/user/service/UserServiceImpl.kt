package at.fhtw.swen3.paperless.user.service

import at.fhtw.swen3.paperless.auth.repository.AccessTokenRepository
import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.team.mapper.TeamMemberEntityMapper
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.team.repository.TeamMemberRepository
import at.fhtw.swen3.paperless.team.repository.TeamRepository
import at.fhtw.swen3.paperless.user.mapper.UserEntityMapper
import at.fhtw.swen3.paperless.user.model.User
import at.fhtw.swen3.paperless.user.repository.UserRepository
import jakarta.validation.ConstraintViolationException
import org.springframework.stereotype.Service
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val teamMemberRepository: TeamMemberRepository,
    private val teamRepository: TeamRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenRepository: AccessTokenRepository
) : UserService {

    @Transactional(readOnly = true)
    override fun searchUsers(): List<User> =
        userRepository.findAll().map(UserEntityMapper::toModel)

    override fun create(user: User): User {
        validateUsername(user.username)
        validatePassword(user.password)
        ensureUsernameAvailable(user.username)

        val entity = UserEntityMapper.toEntity(user.copy(password = requireNotNull(passwordEncoder.encode(user.password))))
        val saved = userRepository.save(entity)

        return UserEntityMapper.toModel(saved)
    }

    @Transactional(readOnly = true)
    override fun getById(id: UUID): User =
        userRepository.findById(id)
            .map(UserEntityMapper::toModel)
            .orElseThrow { AppException(AppErrorMessage.USER_NOT_FOUND) }

    override fun update(id: UUID, username: String?, password: String?): User {
        requireSelf(id)
        username?.let(::validateUsername)
        password?.let(::validatePassword)
        val existing = userRepository.findLockedById(id)
            ?: throw AppException(AppErrorMessage.USER_NOT_FOUND)
        val current = UserEntityMapper.toModel(existing)
        val updatedUsername = username ?: current.username
        if (updatedUsername != current.username) {
            ensureUsernameAvailable(updatedUsername)
        }

        val updated = current.copy(
            username = updatedUsername,
            password = password?.let { requireNotNull(passwordEncoder.encode(it)) } ?: current.password,
            updatedAt = Instant.now()
        )
        val saved = userRepository.save(UserEntityMapper.toEntity(updated))
        if (password != null) tokenRepository.deleteByUserId(id)

        return UserEntityMapper.toModel(saved)
    }

    override fun delete(id: UUID) {
        requireSelf(id)
        userRepository.findLockedById(id)
            ?: throw AppException(AppErrorMessage.USER_NOT_FOUND)

        val memberships = teamMemberRepository.findByUserId(id)
        // ponytail: scans teams to cover owners whose membership was removed; add a filtered owner lookup if team volume warrants it.
        val ownedTeamIds = teamRepository.findAll().asSequence().filter { it.ownerId == id }
            .map { requireNotNull(it.id) }.toList()
        val teamIds = (memberships.map { requireNotNull(it.team.id) } + ownedTeamIds).distinct().sorted()
        val lockedTeams = teamIds.mapNotNull(teamRepository::findLockedById)
        lockedTeams.forEach { team ->
            val memberships = teamMemberRepository.findByTeamId(requireNotNull(team.id))
            if (team.ownerId == id) {
                throw AppException(AppErrorMessage.USER_OWNS_TEAM)
            }
            val admins = memberships.filter { it.role == Role.ADMIN }
            if (admins.size == 1 && admins.single().user.id == id) {
                throw AppException(AppErrorMessage.LAST_TEAM_ADMIN)
            }
        }

        tokenRepository.deleteByUserId(id)
        teamMemberRepository.deleteAll(teamMemberRepository.findByUserId(id))
        userRepository.deleteById(id)
    }

    @Transactional(readOnly = true)
    override fun getTeams(id: UUID): List<TeamMember> {
        requireSelf(id)
        getById(id)

        return teamMemberRepository.findByUserId(id).map(TeamMemberEntityMapper::toModel)
    }

    override fun authenticate(username: String, password: String): User {
        val user = userRepository.findLockedByUsername(username)
            ?: throw AppException(AppErrorMessage.INVALID_CREDENTIALS)

        if (!user.password.startsWith("{pbkdf2@SpringSecurity_v5_8}") || !passwordEncoder.matches(password, user.password)) {
            throw AppException(AppErrorMessage.INVALID_CREDENTIALS)
        }

        return UserEntityMapper.toModel(user)
    }

    private fun ensureUsernameAvailable(username: String) {
        val duplicate = userRepository.findByUsername(username)
        if (duplicate != null) {
            throw AppException(AppErrorMessage.USERNAME_ALREADY_EXISTS)
        }
    }

    private fun validateUsername(username: String) {
        if (username.isBlank() || username.length !in 4..50) {
            throw ConstraintViolationException(
                "Username must be non-blank and between 4 and 50 characters.",
                emptySet()
            )
        }
    }

    private fun validatePassword(password: String) {
        if (password.length !in 8..100) {
            throw ConstraintViolationException("Password must be between 8 and 100 characters.", emptySet())
        }
    }

    private fun requireSelf(id: UUID) {
        if (SecurityContextHolder.getContext().authentication?.name != id.toString()) {
            throw AccessDeniedException("Only the account owner can modify this user")
        }
    }
}
