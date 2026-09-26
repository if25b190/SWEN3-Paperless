package at.fhtw.swen3.paperless.user.entity

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
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @field:NotBlank
    @field:Size(min = 4, max = 50)
    @Column(nullable = false, unique = true)
    val username: String = "",
    @field:NotBlank
    @Column(nullable = false)
    val password: String = "",
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant? = null
)
