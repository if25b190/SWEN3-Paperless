package at.fhtw.swen3.paperless.document.model

data class DocumentUpload(
    val title: String,
    val originalFilename: String,
    val contentType: String,
    val content: ByteArray,
    val correspondentId: Long?,
    val documentTypeId: Long?
)
