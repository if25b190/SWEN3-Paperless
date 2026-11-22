package at.fhtw.swen3.paperless.search.model

import java.util.UUID

data class SearchHit(val documentId: UUID, val score: Float, val highlights: List<String>)
