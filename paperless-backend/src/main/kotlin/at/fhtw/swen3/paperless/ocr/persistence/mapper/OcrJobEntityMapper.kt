package at.fhtw.swen3.paperless.ocr.persistence.mapper

import at.fhtw.swen3.paperless.ocr.entity.OcrJobEntity
import at.fhtw.swen3.paperless.ocr.model.OcrJob

object OcrJobEntityMapper {
    fun toModel(entity: OcrJobEntity): OcrJob =
        OcrJob(entity.id, entity.documentId, entity.storageKey, entity.contentType)

    fun toEntity(job: OcrJob): OcrJobEntity =
        OcrJobEntity(job.documentId, job.storageKey, job.contentType)
}
