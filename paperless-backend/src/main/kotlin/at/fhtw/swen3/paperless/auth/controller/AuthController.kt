package at.fhtw.swen3.paperless.auth.controller

import at.fhtw.swen3.paperless.api.AuthApi
import at.fhtw.swen3.paperless.auth.mapper.AuthMapper
import at.fhtw.swen3.paperless.auth.model.AuthenticatedPrincipal
import at.fhtw.swen3.paperless.auth.service.AuthService
import at.fhtw.swen3.paperless.dto.LoginRequest
import at.fhtw.swen3.paperless.dto.LoginResponse
import at.fhtw.swen3.paperless.dto.UserResponse
import at.fhtw.swen3.paperless.user.mapper.UserMapper
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val authService: AuthService,
    private val authMapper: AuthMapper,
    private val userMapper: UserMapper
) : AuthApi {

    override fun login(loginRequest: LoginRequest, authentication: Authentication?): ResponseEntity<LoginResponse> {
        val credentials = authMapper.toCredentials(loginRequest)
        val authenticated = authService.login(credentials)
        val response = LoginResponse(
            token = authenticated.token,
            tokenType = "Bearer",
            user = userMapper.toDto(authenticated.user)
        )

        return ResponseEntity.ok(response)
    }

    override fun getCurrentUser(authentication: Authentication?): ResponseEntity<UserResponse> {
        val userId = AuthenticatedPrincipal.userId(authentication)
        val user = authService.getCurrentUser(userId)
        val response = userMapper.toDto(user)

        return ResponseEntity.ok(response)
    }
}
