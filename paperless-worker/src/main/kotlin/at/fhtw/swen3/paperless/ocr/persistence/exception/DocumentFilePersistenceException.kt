package at.fhtw.swen3.paperless.ocr.persistence.exception

class DocumentFilePersistenceException(cause: Throwable) :
    RuntimeException("Document file storage operation failed.", cause)
