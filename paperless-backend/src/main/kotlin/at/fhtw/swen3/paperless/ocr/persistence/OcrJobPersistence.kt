package at.fhtw.swen3.paperless.ocr.persistence

import at.fhtw.swen3.paperless.ocr.model.OcrJob
import java.util.UUID

interface OcrJobPersistence {
    fun enqueue(job: OcrJob)
    fun findNextForDispatch(): OcrJob?
    fun delete(id: UUID)
}
