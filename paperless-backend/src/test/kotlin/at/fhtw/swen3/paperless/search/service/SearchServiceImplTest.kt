package at.fhtw.swen3.paperless.search.service

import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.persistence.DocumentPersistence
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.model.Team
import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.team.persistence.TeamMemberPersistence
import at.fhtw.swen3.paperless.user.model.User
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class SearchServiceImplTest {

    @Mock
    lateinit var documentRepository: DocumentPersistence

    @Mock
    lateinit var teamMemberRepository: TeamMemberPersistence

    @InjectMocks
    lateinit var service: SearchServiceImpl

    @Test
    fun search_multi_term_and_pagination_ok() {
        // given
        `when`(documentRepository.findOwnerOnlyDocuments(uuid(1))).thenReturn(
            listOf(
                document(uuid(1), "Invoice", "Invoice from Acme"),
                document(uuid(2), "Invoice", "Invoice from another sender")
            )
        )

        // when
        val firstPage = service.search(uuid(1), "invoice acme", false, 0, 1)
        val secondPage = service.search(uuid(1), "invoice acme", false, 1, 1)

        // then
        assertAll(
            { assertThat(firstPage.totalElements).isEqualTo(2) },
            { assertThat(firstPage.content.single().document.id).isEqualTo(uuid(1)) },
            { assertThat(firstPage.content.single().score).isEqualTo(2.0f) },
            {
                assertThat(firstPage.content.single().highlights).containsExactly(
                    "<em>invoice</em>",
                    "<em>invoice</em> from <em>acme</em>",
                    "An <em>invoice</em>"
                )
            },
            { assertThat(secondPage.content.single().document.id).isEqualTo(uuid(2)) }
        )
    }

    @Test
    fun search_fuzzy_terms_ok() {
        // given
        `when`(documentRepository.findOwnerOnlyDocuments(uuid(1))).thenReturn(listOf(document(uuid(1), "Invoice", "Invoice from Acme")))

        // when
        val result = service.search(uuid(1), "invoic acm", true, 0, 10)

        // then
        assertAll(
            { assertThat(result.totalElements).isEqualTo(1) },
            { assertThat(result.content.single().score).isEqualTo(2.0f) }
        )
    }

    @Test
    fun search_without_matches_returns_empty_page_ok() {
        // given
        `when`(documentRepository.findOwnerOnlyDocuments(uuid(1))).thenReturn(listOf(document(uuid(1), "Invoice", "Invoice from Acme")))

        // when
        val result = service.search(uuid(1), "passport", false, 0, 10)

        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun search_does_not_expose_candidates_excluded_by_visibility_boundary() {
        // given
        val hiddenCandidate = document(uuid(2), "Secret report", "project-aurora")
        `when`(documentRepository.findOwnerOnlyDocuments(uuid(1))).thenReturn(
            listOf(document(uuid(1), "Invoice", "Invoice from Acme"))
        )

        // when
        val result = service.search(uuid(1), hiddenCandidate.ocrContent!!, false, 0, 10)

        // then
        assertAll(
            { assertThat(result.content).isEmpty() },
            { assertThat(result.totalElements).isZero() },
            { assertThat(result.content.flatMap { it.highlights }).isEmpty() }
        )
        verify(documentRepository).findOwnerOnlyDocuments(uuid(1))
    }

    @Test
    fun search_with_maximum_page_returns_empty_page_without_overflow() {
        // given
        `when`(documentRepository.findOwnerOnlyDocuments(uuid(1))).thenReturn(listOf(document(uuid(1), "Invoice", "Invoice from Acme")))

        // when
        val result = service.search(uuid(1), "invoice", false, Int.MAX_VALUE, 100)

        // then
        assertAll(
            { assertThat(result.content).isEmpty() },
            { assertThat(result.totalElements).isEqualTo(1) }
        )
    }

    @Test
    fun team_search_uses_current_memberships_to_select_candidates_ok() {
        // given
        val membership = TeamMember(
            Team(uuid(4), "Team", null, Instant.EPOCH, null),
            User(uuid(1), "alice", "unused", Instant.EPOCH, null), Role.READONLY, Instant.EPOCH
        )
        `when`(teamMemberRepository.findByUserId(uuid(1))).thenReturn(listOf(membership))
        `when`(documentRepository.findVisibleDocuments(uuid(1), setOf(uuid(4))))
            .thenReturn(listOf(document(uuid(3), "Invoice", "Invoice from Acme").copy(teamId = uuid(4))))

        // when
        val result = service.search(uuid(1), "invoice", false, 0, 10)

        // then
        assertThat(result.content.single().document.teamId).isEqualTo(uuid(4))
        verify(documentRepository).findVisibleDocuments(uuid(1), setOf(uuid(4)))
    }

    private fun uuid(value: Int) = UUID(0L, value.toLong())

    private fun document(id: UUID, title: String, text: String) = Document(
        id = id,
        title = title,
        originalFilename = "document.pdf",
        contentType = "application/pdf",
        fileSize = 10,
        status = ProcessingStatus.COMPLETED,
        ocrContent = text,
        summary = "An invoice",
        storageKey = null,
        documentType = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        ownerId = uuid(1)
    )
}
