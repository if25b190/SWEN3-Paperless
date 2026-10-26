package at.fhtw.swen3.paperless.ocr.entity

import at.fhtw.swen3.paperless.common.entity.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "ocr_jobs")
class OcrJobEntity(
    @Column(nullable = false, unique = true)
    val documentId: UUID,
    @Column(nullable = false)
    val storageKey: String,
    @Column(nullable = false)
    val contentType: String
) : AbstractEntity()
