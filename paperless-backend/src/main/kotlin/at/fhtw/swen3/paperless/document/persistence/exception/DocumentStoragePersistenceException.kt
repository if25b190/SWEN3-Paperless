package at.fhtw.swen3.paperless.document.persistence.exception

class DocumentStoragePersistenceException(cause: Throwable) :
    RuntimeException("Document storage operation failed.", cause)
