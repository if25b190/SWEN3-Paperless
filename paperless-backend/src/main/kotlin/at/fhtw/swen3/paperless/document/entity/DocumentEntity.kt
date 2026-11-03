package at.fhtw.swen3.paperless.document.entity

import at.fhtw.swen3.paperless.common.entity.AbstractEntity
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.user.entity.UserEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

@Entity
@Table(name = "documents")
class DocumentEntity(
    @field:NotBlank
    @field:Size(max = 255)
    @Column(nullable = false)
    var title: String = "",
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
    val owner: UserEntity = UserEntity(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    var team: TeamEntity? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProcessingStatus = ProcessingStatus.PENDING,
    @Column(columnDefinition = "TEXT")
    var ocrContent: String? = null,
    @Column(columnDefinition = "TEXT")
    var summary: String? = null,
    var storageKey: String? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_type_id")
    var documentType: DocumentTypeEntity? = null
) : AbstractEntity()
