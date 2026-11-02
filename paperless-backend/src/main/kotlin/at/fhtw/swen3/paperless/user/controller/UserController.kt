package at.fhtw.swen3.paperless.user.controller

import at.fhtw.swen3.paperless.api.UsersApi
import at.fhtw.swen3.paperless.auth.model.AuthenticatedPrincipal
import at.fhtw.swen3.paperless.dto.CreateUserRequest
import at.fhtw.swen3.paperless.dto.UpdateUserRequest
import at.fhtw.swen3.paperless.dto.UserListResponse
import at.fhtw.swen3.paperless.dto.UserResponse
import at.fhtw.swen3.paperless.dto.UserTeamMembershipListResponse
import at.fhtw.swen3.paperless.team.mapper.TeamMemberMapper
import at.fhtw.swen3.paperless.user.mapper.UserMapper
import at.fhtw.swen3.paperless.user.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant
import java.util.UUID

@RestController
class UserController(
    private val userService: UserService,
    private val userMapper: UserMapper,
    private val teamMemberMapper: TeamMemberMapper
) : UsersApi {

    override fun createUser(
        createUserRequest: CreateUserRequest,
        authentication: Authentication?
    ): ResponseEntity<UserResponse> {
        val user = userMapper.fromCreateDto(createUserRequest, Instant.now())
        val created = userService.create(user)
        val response = userMapper.toDto(created)

        return ResponseEntity.created(URI.create("/api/v1/users/${created.id}")).body(response)
    }

    override fun deleteUser(id: UUID, authentication: Authentication?): ResponseEntity<Unit> {
        userService.delete(AuthenticatedPrincipal.userId(authentication), id)

        return ResponseEntity.noContent().build()
    }

    override fun getUserById(id: UUID, authentication: Authentication?): ResponseEntity<UserResponse> {
        val user = userService.getById(id)
        val response = userMapper.toDto(user)

        return ResponseEntity.ok(response)
    }

    override fun getUserTeams(id: UUID, authentication: Authentication?): ResponseEntity<UserTeamMembershipListResponse> {
        val memberships = userService.getTeams(AuthenticatedPrincipal.userId(authentication), id)
        val items = memberships.map(teamMemberMapper::toMembershipDto)
        val response = UserTeamMembershipListResponse(items)

        return ResponseEntity.ok(response)
    }

    override fun getUsers(authentication: Authentication?): ResponseEntity<UserListResponse> {
        val users = userService.searchUsers()
        val items = users.map(userMapper::toDto)
        val response = UserListResponse(items)

        return ResponseEntity.ok(response)
    }

    override fun updateUser(
        id: UUID,
        updateUserRequest: UpdateUserRequest,
        authentication: Authentication?
    ): ResponseEntity<UserResponse> {
        val updated = userService.update(
            AuthenticatedPrincipal.userId(authentication),
            id,
            updateUserRequest.username,
            updateUserRequest.password
        )
        val response = userMapper.toDto(updated)

        return ResponseEntity.ok(response)
    }
}
