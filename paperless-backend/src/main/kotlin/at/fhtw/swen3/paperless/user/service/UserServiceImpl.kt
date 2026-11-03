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
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.security.access.AccessDeniedException
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

    override fun update(actorId: UUID, id: UUID, username: String?, password: String?): User {
        requireSelf(actorId, id)
        val current = userRepository.findLockedById(id)
            ?: throw UserNotFoundException()
        val updatedUsername = username ?: current.username
        if (updatedUsername != current.username) {
            ensureUsernameAvailable(updatedUsername)
        }

        val updated = current.copy(
            username = updatedUsername,
            password = password?.let { requireNotNull(passwordEncoder.encode(it)) } ?: current.password
        )
        if (password != null) tokenRepository.deleteByUserId(id)
        log.info("[USER] - ACTION: update: userId: {}", id)

        return userRepository.update(id, updated)
    }

    override fun delete(actorId: UUID, id: UUID) {
        requireSelf(actorId, id)
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
    override fun getTeams(actorId: UUID, id: UUID): List<TeamMember> {
        requireSelf(actorId, id)
        getById(id)

        return teamMemberRepository.findByUserId(id)
    }

    private fun ensureUsernameAvailable(username: String) {
        val duplicate = userRepository.findByUsername(username)
        if (duplicate != null) {
            throw UsernameAlreadyExistsException()
        }
    }

    private fun requireSelf(actorId: UUID, id: UUID) {
        if (actorId != id) {
            throw AccessDeniedException("Only the account owner can modify this user")
        }
    }
}
