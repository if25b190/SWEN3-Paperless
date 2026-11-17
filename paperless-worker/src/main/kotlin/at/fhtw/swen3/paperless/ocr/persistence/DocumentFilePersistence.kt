package at.fhtw.swen3.paperless.ocr.persistence

/**
 * Domain-facing access to the stored document bytes (e.g. object storage).
 * Returns `null` when no object exists for the given storage key.
 */
interface DocumentFilePersistence {
    fun find(storageKey: String): ByteArray?
}
