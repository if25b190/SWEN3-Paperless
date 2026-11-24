package at.fhtw.swen3.paperless.ocr.persistence

import java.nio.file.Path

interface DocumentFilePersistence {
    /** The path is valid for the duration of [action]. Returns null when the source is missing. */
    fun <T : Any> withLocalFile(storageKey: String, action: (Path) -> T): T?
}
