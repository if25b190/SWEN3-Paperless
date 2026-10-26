package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.persistence.DocumentPersistence
import at.fhtw.swen3.paperless.ocr.model.OcrResult
import at.fhtw.swen3.paperless.ocr.model.OcrResultStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class OcrResultServiceImpl(private val documents: DocumentPersistence) : OcrResultService {
    override fun apply(result: OcrResult) {
        if (result.status == OcrResultStatus.COMPLETED) {
            requireNotNull(result.text) { "Completed OCR results must contain text." }
            require(result.text.length <= MAX_TEXT_CHARS) { "OCR result exceeds the text limit." }
        }
        val document = documents.findLockedById(result.documentId) ?: return
        if (document.storageKey != result.storageKey) return
        if (document.status == ProcessingStatus.COMPLETED && result.status == OcrResultStatus.FAILED) return
        val status = when (result.status) {
            OcrResultStatus.COMPLETED -> ProcessingStatus.COMPLETED
            OcrResultStatus.FAILED -> ProcessingStatus.FAILED
        }
        val text = if (result.status == OcrResultStatus.COMPLETED) result.text else document.ocrContent
        if (document.status == status && document.ocrContent == text) return

        documents.update(result.documentId, document.copy(ocrContent = text, status = status))
        logger.info(
            "[OCR] - ACTION: applyResult: jobId: {}, documentId: {}, status: {}",
            result.jobId, result.documentId, status
        )
    }

    private companion object {
        const val MAX_TEXT_CHARS = 2_000_000
        val logger = LoggerFactory.getLogger(OcrResultServiceImpl::class.java)
    }
}
