package at.fhtw.swen3.paperless.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, unique = true)
    val username: String = "",
    @Column(nullable = false, unique = true)
    val email: String = "",
    @Column(nullable = false)
    val password: String = "",
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant? = null
)
