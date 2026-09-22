package at.fhtw.swen3.paperless.document.model

data class DocumentUpdate(
    val title: String?,
    val correspondentId: Long?,
    val documentTypeId: Long?
)
