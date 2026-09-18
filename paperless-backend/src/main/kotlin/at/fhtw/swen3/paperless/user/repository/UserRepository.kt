package at.fhtw.swen3.paperless.user.repository

import at.fhtw.swen3.paperless.user.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<UserEntity, Long> {

    fun findByUsername(username: String): UserEntity?
}
