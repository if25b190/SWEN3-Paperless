package at.fhtw.swen3.paperless.exception

class DocumentConflictException(cause: Throwable? = null) : AppException(AppErrorMessage.RESOURCE_CONFLICT, cause)
