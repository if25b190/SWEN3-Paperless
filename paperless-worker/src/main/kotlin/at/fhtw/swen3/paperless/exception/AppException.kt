package at.fhtw.swen3.paperless.exception

class AppException(val error: AppErrorMessage, cause: Throwable? = null) : RuntimeException(error.message, cause)
