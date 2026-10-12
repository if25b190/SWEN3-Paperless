package at.fhtw.swen3.paperless.search.service

import at.fhtw.swen3.paperless.auth.model.AuthenticatedPrincipal
import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.persistence.DocumentPersistence
import at.fhtw.swen3.paperless.search.model.SearchResult
import at.fhtw.swen3.paperless.team.persistence.TeamMemberPersistence
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SearchServiceImpl(
    private val documentRepository: DocumentPersistence,
    private val teamMemberRepository: TeamMemberPersistence
) : SearchService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun search(query: String, fuzzy: Boolean, page: Int, size: Int): Page<SearchResult> {
        if (page < 0) throw ConstraintViolationException("page must be non-negative", emptySet())
        if (size !in 1..100) throw ConstraintViolationException("size must be between 1 and 100", emptySet())

        val terms = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
        val actorId = AuthenticatedPrincipal.userId(SecurityContextHolder.getContext().authentication?.name)
        val teamIds = teamMemberRepository.findByUserId(actorId).mapTo(mutableSetOf()) { requireNotNull(it.team.id) }
        val documents = if (teamIds.isEmpty()) {
            documentRepository.findOwnerOnlyDocuments(actorId)
        } else {
            documentRepository.findVisibleDocuments(actorId, teamIds)
        }
        val results = documents.mapNotNull { document ->
            val searchable = listOfNotNull(document.title, document.ocrContent, document.summary)
                .joinToString(" ").lowercase()
            val matched = terms.filter { term ->
                searchable.contains(term) || (fuzzy && searchable.split(Regex("\\W+")).any { fuzzyMatch(term, it) })
            }
            if (matched.isEmpty()) null else SearchResult(document, matched.size.toFloat(), highlights(document, matched))
        }.sortedByDescending(SearchResult::score)

        val request = PageRequest.of(page, size)
        val from = minOf(page.toLong() * size, results.size.toLong()).toInt()
        val to = minOf(from.toLong() + size, results.size.toLong()).toInt()
        log.debug("[SEARCH] - ACTION: search: page: {}, size: {}, resultCount: {}", page, size, results.size)

        return PageImpl(results.subList(from, to), request, results.size.toLong())
    }

    private fun fuzzyMatch(term: String, word: String): Boolean =
        term.length > 2 && word.length > 2 && levenshtein(term, word) <= 1

    private fun levenshtein(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    private fun highlights(
        document: Document,
        terms: List<String>
    ): List<String> = listOfNotNull(document.title, document.ocrContent, document.summary)
        .flatMap { text ->
            terms.fold(listOf(text)) { snippets, term ->
                snippets.map { it.replace(Regex("(?i)\\b${Regex.escape(term)}\\b"), "<em>$term</em>") }
            }
        }
        .filter { it.contains("<em>") }
        .take(3)
}
