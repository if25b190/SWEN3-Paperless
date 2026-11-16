package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import at.fhtw.swen3.paperless.ocr.engine.OcrEngine
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.model.ProcessingStatus
import at.fhtw.swen3.paperless.ocr.model.WorkerDocument
import at.fhtw.swen3.paperless.ocr.persistence.DocumentFilePersistence
import at.fhtw.swen3.paperless.ocr.persistence.DocumentIndexPersistence
import at.fhtw.swen3.paperless.ocr.persistence.OcrDocumentPersistence
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path

@Service
@Transactional(noRollbackFor = [AppException::class])
class OcrProcessingServiceImpl(
    private val documents: OcrDocumentPersistence,
    private val engine: OcrEngine,
    private val index: DocumentIndexPersistence,
    private val properties: OcrWorkerProperties,
    private val files: DocumentFilePersistence? = null,
) : OcrProcessingService {
    override fun process(request: OcrRequest) {
        val document = documents.findLockedById(request.documentId) ?: return
        val retryable = document.status == ProcessingStatus.PENDING || document.status == ProcessingStatus.FAILED
        if (!retryable) {
            logger.info(
                "[OCR] - ACTION: process: skipped duplicate: jobId: {}, documentId: {}",
                request.jobId,
                request.documentId,
            )

            return
        }
        val claimed = documents.update(document.copy(status = ProcessingStatus.OCR_IN_PROGRESS, ocrContent = null))
        val text = try {
            val (source, downloaded) = resolveSource(request.storageKey)
            try {
                val extracted = engine.extractText(source)
                index.index(request.documentId, extracted)
                extracted
            } finally {
                if (downloaded != null) {
                    runCatching { Files.deleteIfExists(downloaded) }
                }
            }
        } catch (exception: Exception) {
            markFailed(claimed, exception)
            throw AppException(AppErrorMessage.OCR_PROCESSING_FAILED, exception)
        }
        documents.update(claimed.copy(status = properties.finalStatus, ocrContent = text))
        logger.info(
            "[OCR] - ACTION: process: completed: jobId: {}, documentId: {}, textLength: {}, finalStatus: {}",
            request.jobId,
            request.documentId,
            text.length,
            properties.finalStatus,
        )
    }

    /**
     * Resolves the OCR source file: the shared volume first (legacy flat layout),
     * otherwise the object is downloaded from storage by its full key into a
     * temporary file that the caller deletes after processing.
     */
    private fun resolveSource(storageKey: String): Pair<Path, Path?> {
        val fileName = storageKey.substringAfterLast('/')
        val local = properties.storageDir.resolve(fileName)
        if (Files.isRegularFile(local)) return local to null
        val bytes = files?.find(storageKey) ?: return local to null
        val downloaded = Files.createTempFile("paperless-ocr-source-", "-$fileName")
        Files.write(downloaded, bytes)
        logger.info("[OCR] - ACTION: resolveSource: downloaded: storageKey: {}, bytes: {}", storageKey, bytes.size)
        return downloaded to downloaded
    }

    private fun markFailed(document: WorkerDocument, failure: Exception) {
        try {
            documents.update(document.copy(status = ProcessingStatus.FAILED, ocrContent = null))
        } catch (exception: Exception) {
            exception.addSuppressed(failure)
            throw exception
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(OcrProcessingServiceImpl::class.java)
    }
}
