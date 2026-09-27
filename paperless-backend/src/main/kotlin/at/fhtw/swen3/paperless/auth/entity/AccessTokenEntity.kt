package at.fhtw.swen3.paperless.auth.entity

import at.fhtw.swen3.paperless.user.entity.UserEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.validation.constraints.Pattern
import java.time.Instant

@Entity
@Table(name = "access_tokens", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id"])])
class AccessTokenEntity(
    @Id
    @field:Pattern(regexp = "[0-9a-f]{64}")
    @Column(length = 64)
    val digest: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,
    @Column(nullable = false)
    val expiresAt: Instant
)
