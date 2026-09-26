package at.fhtw.swen3.paperless.search.service

import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.service.DocumentService
import at.fhtw.swen3.paperless.search.model.SearchResult
import jakarta.validation.ConstraintViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SearchServiceImpl(private val documentService: DocumentService) : SearchService {

    override fun search(query: String, fuzzy: Boolean, page: Int, size: Int): Page<SearchResult> {
        if (page < 0) throw ConstraintViolationException("page must be non-negative", emptySet())
        if (size !in 1..100) throw ConstraintViolationException("size must be between 1 and 100", emptySet())

        val terms = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
        val results = documentService.visibleDocumentsForSearch().mapNotNull { document ->
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
