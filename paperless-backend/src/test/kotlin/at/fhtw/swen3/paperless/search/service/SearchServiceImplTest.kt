package at.fhtw.swen3.paperless.search.service

import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.persistence.DocumentPersistence
import at.fhtw.swen3.paperless.search.model.SearchHit
import at.fhtw.swen3.paperless.search.model.SearchQuery
import at.fhtw.swen3.paperless.search.persistence.SearchPersistence
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.model.Team
import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.team.persistence.TeamMemberPersistence
import at.fhtw.swen3.paperless.user.model.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SearchServiceImplTest {
    @Mock lateinit var documentRepository: DocumentPersistence

    @Mock lateinit var teamMemberRepository: TeamMemberPersistence

    @Mock lateinit var searchPersistence: SearchPersistence

    @InjectMocks lateinit var service: SearchServiceImpl

    @Test
    fun search_preserves_elasticsearch_scores_highlights_and_pagination_ok() {
        // given
        val pageable = PageRequest.of(1, 1)
        val hit = SearchHit(uuid(2), 4.5f, listOf("An <em>invoice</em>"))
        `when`(
            documentRepository.findOwnerOnlyDocuments(uuid(1)),
        ).thenReturn(listOf(document(uuid(1)), document(uuid(2))))
        `when`(searchPersistence.search(setOf(uuid(1), uuid(2)), SearchQuery("invoice acme", false), pageable))
            .thenReturn(PageImpl(listOf(hit), pageable, 2))

        // when
        val result = service.search(uuid(1), " invoice acme ", false, 1, 1)

        // then
        assertAll(
            { assertThat(result.totalElements).isEqualTo(2) },
            { assertThat(result.number).isEqualTo(1) },
            { assertThat(result.content.single().document).isEqualTo(document(uuid(2))) },
            { assertThat(result.content.single().score).isEqualTo(4.5f) },
            { assertThat(result.content.single().highlights).containsExactly("An <em>invoice</em>") },
        )
    }

    @Test
    fun search_passes_fuzzy_flag_and_only_visible_ids_ok() {
        // given
        val pageable = PageRequest.of(0, 10)
        `when`(documentRepository.findOwnerOnlyDocuments(uuid(1))).thenReturn(listOf(document(uuid(1))))
        `when`(searchPersistence.search(setOf(uuid(1)), SearchQuery("invoic", true), pageable))
            .thenReturn(PageImpl(listOf(SearchHit(uuid(1), 2.0f, emptyList())), pageable, 1))

        // when
        val result = service.search(uuid(1), "invoic", true, 0, 10)

        // then
        assertThat(result.content.single().document.id).isEqualTo(uuid(1))
        verify(searchPersistence).search(setOf(uuid(1)), SearchQuery("invoic", true), pageable)
    }

    @Test
    fun search_without_matches_returns_empty_page_ok() {
        // given
        val pageable = PageRequest.of(0, 10)
        `when`(documentRepository.findOwnerOnlyDocuments(uuid(1))).thenReturn(listOf(document(uuid(1))))
        `when`(searchPersistence.search(setOf(uuid(1)), SearchQuery("passport", false), pageable))
            .thenReturn(Page.empty(pageable))

        // when
        val result = service.search(uuid(1), "passport", false, 0, 10)

        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun search_without_visible_documents_skips_elasticsearch_ok() {
        // when
        val result = service.search(uuid(1), "invoice", false, 0, 10)

        // then
        assertThat(result.totalElements).isZero()
        verifyNoInteractions(searchPersistence)
    }

    @Test
    fun search_blank_query_skips_persistence_ok() {
        // when
        val result = service.search(uuid(1), "   ", false, 0, 10)

        // then
        assertThat(result).isEmpty()
        verifyNoInteractions(documentRepository, teamMemberRepository, searchPersistence)
    }

    @Test
    fun search_does_not_expose_unexpected_hidden_hit_ok() {
        // given
        val pageable = PageRequest.of(0, 10)
        `when`(documentRepository.findOwnerOnlyDocuments(uuid(1))).thenReturn(listOf(document(uuid(1))))
        `when`(searchPersistence.search(setOf(uuid(1)), SearchQuery("secret", false), pageable))
            .thenReturn(PageImpl(listOf(SearchHit(uuid(2), 9.0f, listOf("secret"))), pageable, 1))

        // when
        val result = service.search(uuid(1), "secret", false, 0, 10)

        // then
        assertThat(result.content).isEmpty()
    }

    @Test
    fun team_search_uses_current_memberships_to_select_candidates_ok() {
        // given
        val membership = TeamMember(
            Team(uuid(4), "Team", null, Instant.EPOCH, null),
            User(uuid(1), "alice", "unused", Instant.EPOCH, null),
            Role.READONLY,
            Instant.EPOCH,
        )
        val pageable = PageRequest.of(0, 10)
        `when`(teamMemberRepository.findByUserId(uuid(1))).thenReturn(listOf(membership))
        `when`(documentRepository.findVisibleDocuments(uuid(1), setOf(uuid(4))))
            .thenReturn(listOf(document(uuid(3)).copy(teamId = uuid(4))))
        `when`(searchPersistence.search(setOf(uuid(3)), SearchQuery("invoice", false), pageable))
            .thenReturn(PageImpl(listOf(SearchHit(uuid(3), 1.0f, emptyList())), pageable, 1))

        // when
        val result = service.search(uuid(1), "invoice", false, 0, 10)

        // then
        assertThat(result.content.single().document.teamId).isEqualTo(uuid(4))
        verify(searchPersistence).search(setOf(uuid(3)), SearchQuery("invoice", false), pageable)
    }

    private fun uuid(value: Int): UUID = UUID(0L, value.toLong())

    private fun document(id: UUID): Document = Document(
        id = id,
        title = "Invoice",
        originalFilename = "document.pdf",
        contentType = "application/pdf",
        fileSize = 10,
        status = ProcessingStatus.COMPLETED,
        ocrContent = "Invoice from Acme",
        summary = "An invoice",
        storageKey = null,
        documentType = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        ownerId = uuid(1),
    )
}
