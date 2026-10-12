package at.fhtw.swen3.paperless.auth.model

import java.time.Instant
import java.util.UUID

data class AccessToken(val digest: String, val userId: UUID, val expiresAt: Instant)
