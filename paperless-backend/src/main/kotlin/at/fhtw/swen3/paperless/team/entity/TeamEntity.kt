package at.fhtw.swen3.paperless.team.entity

import at.fhtw.swen3.paperless.common.entity.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "teams")
class TeamEntity(
    id: UUID? = null,
    @field:NotBlank
    @field:Size(max = 100)
    @Column(nullable = false)
    var name: String = "",
    @field:Size(max = 255)
    var description: String? = null,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant? = null,
    @Column(nullable = false, updatable = false)
    val ownerId: UUID? = null,
    version: Long = 0
) : AbstractEntity(id, createdAt, updatedAt ?: createdAt, version)
