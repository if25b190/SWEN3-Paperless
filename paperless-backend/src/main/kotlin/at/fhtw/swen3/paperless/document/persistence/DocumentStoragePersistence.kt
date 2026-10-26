package at.fhtw.swen3.paperless.document.persistence

interface DocumentStoragePersistence {
    fun store(storageKey: String, content: ByteArray, contentType: String)
    fun find(storageKey: String): ByteArray?
    fun delete(storageKey: String)
}
