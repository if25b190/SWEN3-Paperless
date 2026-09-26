package at.fhtw.swen3.paperless.team.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "teams")
class TeamEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @field:NotBlank
    @field:Size(max = 100)
    @Column(nullable = false)
    var name: String = "",
    @field:Size(max = 255)
    var description: String? = null,
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.EPOCH,
    var updatedAt: Instant? = null,
    @Column(nullable = false, updatable = false)
    val ownerId: UUID? = null
)
