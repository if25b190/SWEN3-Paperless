package at.fhtw.swen3.paperless.auth.controller

import at.fhtw.swen3.paperless.api.AuthApi
import at.fhtw.swen3.paperless.auth.mapper.AuthMapper
import at.fhtw.swen3.paperless.auth.service.AuthService
import at.fhtw.swen3.paperless.dto.LoginRequest
import at.fhtw.swen3.paperless.dto.LoginResponse
import at.fhtw.swen3.paperless.dto.UserResponse
import at.fhtw.swen3.paperless.user.mapper.UserMapper
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@RestController
class AuthController(
    private val authService: AuthService
) : AuthApi {

    override fun login(loginRequest: LoginRequest): ResponseEntity<LoginResponse> {
        val credentials = AuthMapper.toCredentials(loginRequest)
        val authenticated = authService.login(credentials)
        val response = LoginResponse(
            token = authenticated.token,
            tokenType = "Bearer",
            user = UserMapper.toDto(authenticated.user)
        )

        return ResponseEntity.ok(response)
    }

    override fun getCurrentUser(): ResponseEntity<UserResponse> {
        val token = bearerToken()
        val user = authService.getCurrentUser(token)
        val response = UserMapper.toDto(user)

        return ResponseEntity.ok(response)
    }

    private fun bearerToken(): String? {
        val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
        val authorization = attributes?.request?.getHeader("Authorization") ?: return null

        return authorization
            .takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')
            ?.takeIf { it.isNotBlank() }
    }
}
