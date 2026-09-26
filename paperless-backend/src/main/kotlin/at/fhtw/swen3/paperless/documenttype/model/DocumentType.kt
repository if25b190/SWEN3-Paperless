package at.fhtw.swen3.paperless.documenttype.model

import java.util.UUID

data class DocumentType(
    val id: UUID?,
    val name: String,
    val description: String?
)
