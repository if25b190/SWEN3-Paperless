package at.fhtw.swen3.paperless.user.controller

import at.fhtw.swen3.paperless.api.UsersApi
import at.fhtw.swen3.paperless.dto.CreateUserRequest
import at.fhtw.swen3.paperless.dto.UpdateUserRequest
import at.fhtw.swen3.paperless.dto.UserListResponse
import at.fhtw.swen3.paperless.dto.UserResponse
import at.fhtw.swen3.paperless.dto.UserTeamMembershipListResponse
import at.fhtw.swen3.paperless.team.mapper.TeamMemberMapper
import at.fhtw.swen3.paperless.user.mapper.UserMapper
import at.fhtw.swen3.paperless.user.service.UserService
import org.springframework.http.ResponseEntity
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
        createUserRequest: CreateUserRequest
    ): ResponseEntity<UserResponse> {
        val user = userMapper.fromCreateDto(createUserRequest, Instant.now())
        val created = userService.create(user)
        val response = userMapper.toDto(created)

        return ResponseEntity.created(URI.create("/api/v1/users/${created.id}")).body(response)
    }

    override fun deleteUser(id: UUID): ResponseEntity<Unit> {
        userService.delete(id)

        return ResponseEntity.noContent().build()
    }

    override fun getUserById(id: UUID): ResponseEntity<UserResponse> {
        val user = userService.getById(id)
        val response = userMapper.toDto(user)

        return ResponseEntity.ok(response)
    }

    override fun getUserTeams(id: UUID): ResponseEntity<UserTeamMembershipListResponse> {
        val memberships = userService.getTeams(id)
        val items = memberships.map(teamMemberMapper::toMembershipDto)
        val response = UserTeamMembershipListResponse(items)

        return ResponseEntity.ok(response)
    }

    override fun getUsers(): ResponseEntity<UserListResponse> {
        val users = userService.searchUsers()
        val items = users.map(userMapper::toDto)
        val response = UserListResponse(items)

        return ResponseEntity.ok(response)
    }

    override fun updateUser(
        id: UUID,
        updateUserRequest: UpdateUserRequest
    ): ResponseEntity<UserResponse> {
        val updated = userService.update(
            id,
            updateUserRequest.username,
            updateUserRequest.password
        )
        val response = userMapper.toDto(updated)

        return ResponseEntity.ok(response)
    }
}
