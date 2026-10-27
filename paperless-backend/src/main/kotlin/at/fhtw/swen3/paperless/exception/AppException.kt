package at.fhtw.swen3.paperless.exception

sealed class AppException(
    val error: AppErrorMessage,
    cause: Throwable? = null
) : RuntimeException(error.detail, cause)
