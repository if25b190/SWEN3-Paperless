package at.fhtw.swen3.paperless.user.mapper

import at.fhtw.swen3.paperless.dto.CreateUserRequest
import at.fhtw.swen3.paperless.dto.UserResponse
import at.fhtw.swen3.paperless.user.model.User
import java.time.Instant
import java.time.ZoneOffset

object UserMapper {

    fun toDto(user: User): UserResponse =
        UserResponse(
            id = requireNotNull(user.id),
            username = user.username,
            createdAt = user.createdAt.atOffset(ZoneOffset.UTC),
            updatedAt = user.updatedAt?.atOffset(ZoneOffset.UTC)
        )

    fun fromCreateDto(dto: CreateUserRequest, now: Instant): User =
        User(
            id = null,
            username = dto.username,
            password = dto.password,
            createdAt = now,
            updatedAt = null
        )

}
