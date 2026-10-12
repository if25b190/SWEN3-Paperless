package at.fhtw.swen3.paperless.team.entity

import at.fhtw.swen3.paperless.common.entity.AbstractEntity
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.user.entity.UserEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "team_members",
    uniqueConstraints = [UniqueConstraint(columnNames = ["team_id", "user_id"])]
)
class TeamMemberEntity(
    id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    val team: TeamEntity = TeamEntity(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity = UserEntity(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: Role = Role.READ_WRITE,
    val joinedAt: Instant? = null,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant? = null,
    version: Long = 0
) : AbstractEntity(id, createdAt, updatedAt ?: createdAt, version)
