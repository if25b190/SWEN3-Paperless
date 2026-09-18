package at.fhtw.swen3.paperless.team.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "teams")
class TeamEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    var name: String = "",
    var description: String? = null,
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.EPOCH,
    var updatedAt: Instant? = null
)
