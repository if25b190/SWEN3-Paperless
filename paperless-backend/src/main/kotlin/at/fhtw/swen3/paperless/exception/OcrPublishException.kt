package at.fhtw.swen3.paperless.exception

class OcrPublishException(cause: Throwable? = null) : AppException(AppErrorMessage.OCR_PUBLISH_FAILED, cause)
