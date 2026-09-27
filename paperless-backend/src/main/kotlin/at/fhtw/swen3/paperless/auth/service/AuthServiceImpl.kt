package at.fhtw.swen3.paperless.auth.service

import at.fhtw.swen3.paperless.auth.model.AuthenticatedUser
import at.fhtw.swen3.paperless.auth.model.LoginCredentials
import at.fhtw.swen3.paperless.auth.entity.AccessTokenEntity
import at.fhtw.swen3.paperless.auth.repository.AccessTokenRepository
import at.fhtw.swen3.paperless.user.repository.UserRepository
import at.fhtw.swen3.paperless.user.model.User
import at.fhtw.swen3.paperless.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

@Service
@Transactional
class AuthServiceImpl(
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val tokenRepository: AccessTokenRepository
) : AuthService {
    private val random = SecureRandom()

    override fun login(credentials: LoginCredentials): AuthenticatedUser {
        val user = userService.authenticate(credentials.username, credentials.password)
        val userId = requireNotNull(user.id)
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val entity = userRepository.getReferenceById(userId)
        tokenRepository.deleteByUserId(userId)
        tokenRepository.save(AccessTokenEntity(TokenDigests.sha256(token), entity, Instant.now().plus(1, ChronoUnit.DAYS)))

        return AuthenticatedUser(user, token)
    }

    @Transactional(readOnly = true)
    override fun getCurrentUser(userId: UUID): User = userService.getById(userId)
}
