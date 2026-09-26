package at.fhtw.swen3.paperless.document.entity

import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.user.entity.UserEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "documents")
class DocumentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @field:NotBlank
    @field:Size(max = 255)
    @Column(nullable = false)
    val title: String = "",
    @field:NotBlank
    @Column(nullable = false)
    val originalFilename: String = "",
    @field:NotBlank
    @Column(nullable = false)
    val contentType: String = "",
    @field:PositiveOrZero
    @Column(nullable = false)
    val fileSize: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    val owner: UserEntity,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    val team: TeamEntity? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: ProcessingStatus = ProcessingStatus.PENDING,
    @Column(columnDefinition = "TEXT")
    val ocrContent: String? = null,
    @Column(columnDefinition = "TEXT")
    val summary: String? = null,
    val storageKey: String? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_type_id")
    val documentType: DocumentTypeEntity? = null,
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.EPOCH,
    @Column(nullable = false)
    val updatedAt: Instant = Instant.EPOCH,
    @Version
    var version: Long = 0
)
