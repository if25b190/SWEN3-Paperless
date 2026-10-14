package at.fhtw.swen3.paperless.documenttype.entity

import at.fhtw.swen3.paperless.common.entity.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "document_types")
class DocumentTypeEntity(
    id: UUID? = null,
    @field:NotBlank
    @field:Size(max = 100)
    @Column(nullable = false, unique = true)
    val name: String = "",
    val description: String? = null,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant? = null,
    version: Long = 0
) : AbstractEntity(id, createdAt, updatedAt ?: createdAt, version)
