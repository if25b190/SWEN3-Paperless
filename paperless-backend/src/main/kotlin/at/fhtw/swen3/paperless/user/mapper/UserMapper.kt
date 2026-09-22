package at.fhtw.swen3.paperless.user.mapper

import at.fhtw.swen3.paperless.dto.CreateUserRequest
import at.fhtw.swen3.paperless.dto.UpdateUserRequest
import at.fhtw.swen3.paperless.dto.UserResponse
import at.fhtw.swen3.paperless.user.model.User
import java.time.Instant
import java.time.ZoneOffset

object UserMapper {

    fun toDto(user: User): UserResponse =
        UserResponse(
            id = user.id,
            username = user.username,
            email = user.email,
            createdAt = user.createdAt.atOffset(ZoneOffset.UTC),
            updatedAt = user.updatedAt?.atOffset(ZoneOffset.UTC)
        )

    fun fromCreateDto(dto: CreateUserRequest, now: Instant): User =
        User(
            id = 0,
            username = dto.username,
            email = dto.email,
            password = dto.password,
            createdAt = now,
            updatedAt = null
        )

    fun applyUpdate(user: User, dto: UpdateUserRequest, now: Instant): User =
        user.copy(
            username = dto.username ?: user.username,
            email = dto.email ?: user.email,
            password = dto.password ?: user.password,
            updatedAt = now
        )
}
