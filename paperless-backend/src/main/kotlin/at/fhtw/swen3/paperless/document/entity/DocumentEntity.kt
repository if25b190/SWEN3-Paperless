package at.fhtw.swen3.paperless.document.entity

import at.fhtw.swen3.paperless.correspondent.entity.CorrespondentEntity
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
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
import java.time.Instant

@Entity
@Table(name = "documents")
class DocumentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val title: String = "",
    @Column(nullable = false)
    val originalFilename: String = "",
    @Column(nullable = false)
    val contentType: String = "",
    @Column(nullable = false)
    val fileSize: Long = 0,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: ProcessingStatus = ProcessingStatus.PENDING,
    @Column(columnDefinition = "TEXT")
    val ocrContent: String? = null,
    @Column(columnDefinition = "TEXT")
    val summary: String? = null,
    val storageKey: String? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correspondent_id")
    val correspondent: CorrespondentEntity? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_type_id")
    val documentType: DocumentTypeEntity? = null,
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.EPOCH,
    @Column(nullable = false)
    val updatedAt: Instant = Instant.EPOCH
)
