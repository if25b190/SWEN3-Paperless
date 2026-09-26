package at.fhtw.swen3.paperless.user.mapper

import at.fhtw.swen3.paperless.user.entity.UserEntity
import at.fhtw.swen3.paperless.user.model.User

object UserEntityMapper {

    fun toEntity(user: User): UserEntity =
        UserEntity(
            id = user.id,
            username = user.username,
            password = user.password,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )

    fun toModel(entity: UserEntity): User =
        User(
            id = requireNotNull(entity.id),
            username = entity.username,
            password = entity.password,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
}
