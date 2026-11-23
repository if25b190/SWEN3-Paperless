package at.fhtw.swen3.paperless.ocr.messaging

class OcrResultPublishException(cause: Throwable? = null) : RuntimeException("OCR result publication failed", cause)
