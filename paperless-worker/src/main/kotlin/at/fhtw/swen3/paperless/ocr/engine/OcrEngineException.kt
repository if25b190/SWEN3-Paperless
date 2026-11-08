package at.fhtw.swen3.paperless.ocr.engine

class OcrEngineException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
