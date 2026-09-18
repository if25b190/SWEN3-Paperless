package at.fhtw.swen3.paperless.user.model

import java.time.Instant

data class User(
    val id: Long,
    val username: String,
    val email: String,
    val password: String,
    val createdAt: Instant,
    val updatedAt: Instant?
)
