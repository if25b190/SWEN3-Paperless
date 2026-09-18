package at.fhtw.swen3.paperless.team.model

import java.time.Instant

data class Team(
    val id: Long,
    val name: String,
    val description: String?,
    val createdAt: Instant,
    val updatedAt: Instant?
)
