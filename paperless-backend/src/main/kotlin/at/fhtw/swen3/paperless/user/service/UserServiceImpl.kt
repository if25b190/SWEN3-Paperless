package at.fhtw.swen3.paperless.user.service

import at.fhtw.swen3.paperless.auth.persistence.AccessTokenPersistence
import at.fhtw.swen3.paperless.exception.LastTeamAdminException
import at.fhtw.swen3.paperless.exception.UserNotFoundException
import at.fhtw.swen3.paperless.exception.UserOwnsTeamException
import at.fhtw.swen3.paperless.exception.UsernameAlreadyExistsException
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.team.persistence.TeamMemberPersistence
import at.fhtw.swen3.paperless.team.persistence.TeamPersistence
import at.fhtw.swen3.paperless.user.model.User
import at.fhtw.swen3.paperless.user.persistence.UserPersistence
import jakarta.validation.ConstraintViolationException
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserServiceImpl(
    private val userRepository: UserPersistence,
    private val teamMemberRepository: TeamMemberPersistence,
    private val teamRepository: TeamPersistence,
    private val passwordEncoder: PasswordEncoder,
    private val tokenRepository: AccessTokenPersistence
) : UserService {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    override fun searchUsers(): List<User> =
        userRepository.findAll()

    override fun create(user: User): User {
        validateUsername(user.username)
        validatePassword(user.password)
        ensureUsernameAvailable(user.username)

        val encodedUser = user.copy(password = requireNotNull(passwordEncoder.encode(user.password)))
        val saved = userRepository.save(encodedUser)
        log.info("[USER] - ACTION: create: userId: {}", saved.id)

        return saved
    }

    @Transactional(readOnly = true)
    override fun getById(id: UUID): User =
        userRepository.findById(id)
            .orElseThrow { UserNotFoundException() }

    override fun update(id: UUID, username: String?, password: String?): User {
        requireSelf(id)
        username?.let(::validateUsername)
        password?.let(::validatePassword)
        val current = userRepository.findLockedById(id)
            ?: throw UserNotFoundException()
        val updatedUsername = username ?: current.username
        if (updatedUsername != current.username) {
            ensureUsernameAvailable(updatedUsername)
        }

        val updated = current.copy(
            username = updatedUsername,
            password = password?.let { requireNotNull(passwordEncoder.encode(it)) } ?: current.password,
            updatedAt = Instant.now()
        )
        val saved = userRepository.save(updated)
        if (password != null) tokenRepository.deleteByUserId(id)
        log.info("[USER] - ACTION: update: userId: {}", id)

        return saved
    }

    override fun delete(id: UUID) {
        requireSelf(id)
        userRepository.findLockedById(id)
            ?: throw UserNotFoundException()

        val memberships = teamMemberRepository.findByUserId(id)
        // ponytail: scans teams to cover owners whose membership was removed; add a filtered owner lookup if team volume warrants it.
        val ownedTeamIds = teamRepository.findAll().asSequence().filter { it.ownerId == id }
            .map { requireNotNull(it.id) }.toList()
        val teamIds = (memberships.map { requireNotNull(it.team.id) } + ownedTeamIds).distinct().sorted()
        val lockedTeams = teamIds.mapNotNull(teamRepository::findLockedById)
        lockedTeams.forEach { team ->
            val memberships = teamMemberRepository.findByTeamId(requireNotNull(team.id))
            if (team.ownerId == id) {
                throw UserOwnsTeamException()
            }
            val admins = memberships.filter { it.role == Role.ADMIN }
            if (admins.size == 1 && admins.single().user.id == id) {
                throw LastTeamAdminException()
            }
        }

        tokenRepository.deleteByUserId(id)
        teamMemberRepository.deleteAll(teamMemberRepository.findByUserId(id))
        userRepository.deleteById(id)
        log.info("[USER] - ACTION: delete: userId: {}", id)
    }

    @Transactional(readOnly = true)
    override fun getTeams(id: UUID): List<TeamMember> {
        requireSelf(id)
        getById(id)

        return teamMemberRepository.findByUserId(id)
    }

    private fun ensureUsernameAvailable(username: String) {
        val duplicate = userRepository.findByUsername(username)
        if (duplicate != null) {
            throw UsernameAlreadyExistsException()
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
