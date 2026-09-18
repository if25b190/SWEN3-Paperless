package at.fhtw.swen3.paperless.team.model

import at.fhtw.swen3.paperless.user.model.User
import java.time.Instant

data class TeamMember(
    val team: Team,
    val user: User,
    val role: Role,
    val joinedAt: Instant?
)
