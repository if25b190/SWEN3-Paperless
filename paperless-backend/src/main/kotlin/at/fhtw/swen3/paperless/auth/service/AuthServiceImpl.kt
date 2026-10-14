package at.fhtw.swen3.paperless.auth.service

import at.fhtw.swen3.paperless.auth.model.AccessToken
import at.fhtw.swen3.paperless.auth.model.AuthenticatedUser
import at.fhtw.swen3.paperless.auth.model.LoginCredentials
import at.fhtw.swen3.paperless.auth.persistence.AccessTokenPersistence
import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.user.model.User
import at.fhtw.swen3.paperless.user.persistence.UserPersistence
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuthServiceImpl(
    private val userRepository: UserPersistence,
    private val passwordEncoder: PasswordEncoder,
    private val tokenRepository: AccessTokenPersistence
) : AuthService {
    private val random = SecureRandom()

    override fun login(credentials: LoginCredentials): AuthenticatedUser {
        val user = userRepository.findLockedByUsername(credentials.username)
            ?: throw AppException(AppErrorMessage.INVALID_CREDENTIALS)
        if (!user.password.startsWith("{pbkdf2@SpringSecurity_v5_8}") ||
            !passwordEncoder.matches(credentials.password, user.password)) {
            throw AppException(AppErrorMessage.INVALID_CREDENTIALS)
        }
        val userId = requireNotNull(user.id)
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        tokenRepository.deleteByUserId(userId)
        tokenRepository.save(AccessToken(TokenDigests.sha256(token), userId, Instant.now().plus(1, ChronoUnit.DAYS)))

        return AuthenticatedUser(user, token)
    }

    @Transactional(readOnly = true)
    override fun getCurrentUser(userId: UUID): User =
        userRepository.findById(userId).orElseThrow { AppException(AppErrorMessage.USER_NOT_FOUND) }
}
