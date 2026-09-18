package at.fhtw.swen3.paperless.user.mapper

import at.fhtw.swen3.paperless.user.entity.UserEntity
import at.fhtw.swen3.paperless.user.model.User

object UserEntityMapper {

    fun toEntity(user: User): UserEntity =
        UserEntity(
            id = user.id,
            username = user.username,
            email = user.email,
            password = user.password,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )

    fun toModel(entity: UserEntity): User =
        User(
            id = entity.id,
            username = entity.username,
            email = entity.email,
            password = entity.password,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
}
