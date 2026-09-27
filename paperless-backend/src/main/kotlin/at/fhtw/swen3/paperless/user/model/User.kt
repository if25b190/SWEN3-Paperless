package at.fhtw.swen3.paperless.user.model

import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID?,
    val username: String,
    val password: String,
    val createdAt: Instant,
    val updatedAt: Instant?
)
