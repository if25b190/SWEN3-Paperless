package at.fhtw.swen3.paperless.documenttype.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

@Entity
@Table(name = "document_types")
class DocumentTypeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @field:NotBlank
    @field:Size(max = 100)
    @Column(nullable = false, unique = true)
    val name: String = "",
    val description: String? = null
)
