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
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RestController

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
        val userId = AuthenticatedPrincipal.userId(SecurityContextHolder.getContext().authentication?.name)
        val user = authService.getCurrentUser(userId)
        val response = UserMapper.toDto(user)

        return ResponseEntity.ok(response)
    }
}
