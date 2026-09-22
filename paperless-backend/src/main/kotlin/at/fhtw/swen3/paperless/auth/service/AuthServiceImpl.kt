package at.fhtw.swen3.paperless.auth.service

import at.fhtw.swen3.paperless.auth.model.AuthenticatedUser
import at.fhtw.swen3.paperless.auth.model.LoginCredentials
import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.user.model.User
import at.fhtw.swen3.paperless.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
@Transactional
class AuthServiceImpl(
    private val userService: UserService
) : AuthService {

    // ponytail: in-memory opaque bearer tokens, replace with the deployed identity provider when available.
    private val tokens = ConcurrentHashMap<String, Long>()

    override fun login(credentials: LoginCredentials): AuthenticatedUser {
        val user = userService.authenticate(credentials.username, credentials.password)
        val token = UUID.randomUUID().toString()
        tokens[token] = user.id

        return AuthenticatedUser(user, token)
    }

    @Transactional(readOnly = true)
    override fun getCurrentUser(token: String?): User {
        val userId = token?.let(tokens::get)
            ?: throw AppException(AppErrorMessage.AUTHENTICATION_REQUIRED)

        return userService.getById(userId)
    }
}
