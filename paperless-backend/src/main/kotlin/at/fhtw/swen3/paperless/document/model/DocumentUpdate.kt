package at.fhtw.swen3.paperless.document.model

import java.util.UUID

data class DocumentUpdate(
    val teamId: UUID?,
    val clearTeam: Boolean,
    val title: String?,
    val documentTypeId: UUID?
)
