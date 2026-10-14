package at.fhtw.swen3.paperless.user.entity

import at.fhtw.swen3.paperless.common.entity.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class UserEntity(
    id: UUID? = null,
    @field:NotBlank
    @field:Size(min = 4, max = 50)
    @Column(nullable = false, unique = true)
    val username: String = "",
    @field:NotBlank
    @Column(nullable = false)
    val password: String = "",
    createdAt: Instant = Instant.now(),
    updatedAt: Instant? = null,
    version: Long = 0
) : AbstractEntity(id, createdAt, updatedAt ?: createdAt, version)
