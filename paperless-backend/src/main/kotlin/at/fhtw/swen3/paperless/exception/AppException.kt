package at.fhtw.swen3.paperless.exception

class AppException(
    val error: AppErrorMessage
) : RuntimeException(error.detail)
