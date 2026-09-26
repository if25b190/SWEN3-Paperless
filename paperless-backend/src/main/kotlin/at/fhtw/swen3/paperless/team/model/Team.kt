package at.fhtw.swen3.paperless.team.model

import java.time.Instant
import java.util.UUID

data class Team(
    val id: UUID?,
    val name: String,
    val description: String?,
    val createdAt: Instant,
    val updatedAt: Instant?,
    val ownerId: UUID? = null
)
