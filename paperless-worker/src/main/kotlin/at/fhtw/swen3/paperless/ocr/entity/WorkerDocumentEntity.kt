package at.fhtw.swen3.paperless.ocr.entity

import at.fhtw.swen3.paperless.ocr.model.ProcessingStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "documents")
class WorkerDocumentEntity(
    @Id
    val id: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProcessingStatus = ProcessingStatus.PENDING,
    @Column(columnDefinition = "TEXT")
    var ocrContent: String? = null,
    @Version
    @Column(nullable = false)
    var version: Long = 0,
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
)
