package at.fhtw.swen3.paperless.ocr.persistence

import at.fhtw.swen3.paperless.ocr.model.OcrJob
import java.util.UUID

interface OcrJobPersistence {
    fun enqueue(job: OcrJob)
    fun findNextForDispatch(): OcrJob?
    fun findLockedById(id: UUID): OcrJob?
    fun update(job: OcrJob)
    fun delete(id: UUID)
}
