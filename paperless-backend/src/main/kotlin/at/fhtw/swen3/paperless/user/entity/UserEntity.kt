package at.fhtw.swen3.paperless.user.entity

import at.fhtw.swen3.paperless.common.entity.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Entity
@Table(name = "users")
class UserEntity(
    @field:NotBlank
    @field:Size(min = 4, max = 50)
    @Column(nullable = false, unique = true)
    var username: String = "",
    @field:NotBlank
    @Column(nullable = false)
    var password: String = ""
) : AbstractEntity()
