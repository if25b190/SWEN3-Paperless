package at.fhtw.swen3.paperless.exception

import org.springframework.http.HttpStatus

enum class AppErrorMessage(
    val status: HttpStatus,
    val title: String,
    val detail: String
) {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource Not Found", "The requested user was not found."),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource Not Found", "The requested team was not found."),
    TEAM_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource Not Found", "The requested team membership was not found."),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource Not Found", "The requested document was not found."),
    CORRESPONDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource Not Found", "The requested correspondent was not found."),
    DOCUMENT_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource Not Found", "The requested document type was not found."),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Conflict", "The username is already in use."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Conflict", "The email address is already in use."),
    TEAM_MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "Conflict", "The user is already a member of this team."),
    CORRESPONDENT_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Conflict", "The correspondent name is already in use."),
    DOCUMENT_TYPE_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Conflict", "The document type name is already in use."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Unauthorized", "The supplied credentials are invalid."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication is required."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred.")
}
