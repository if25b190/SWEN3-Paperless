package at.fhtw.swen3.paperless.documenttype.entity

import at.fhtw.swen3.paperless.common.entity.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Entity
@Table(name = "document_types")
class DocumentTypeEntity(
    @field:NotBlank
    @field:Size(max = 100)
    @Column(nullable = false, unique = true)
    var name: String = "",
    var description: String? = null
) : AbstractEntity()
