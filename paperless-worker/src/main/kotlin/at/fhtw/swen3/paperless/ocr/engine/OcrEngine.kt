package at.fhtw.swen3.paperless.ocr.engine

import java.nio.file.Path

interface OcrEngine {
    fun extractText(source: Path): String
}
