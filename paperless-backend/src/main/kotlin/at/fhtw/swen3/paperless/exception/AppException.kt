package at.fhtw.swen3.paperless.exception

sealed class AppException(
    val error: AppErrorMessage
) : RuntimeException(error.detail)
