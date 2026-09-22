package at.fhtw.swen3.paperless.correspondent.service

import at.fhtw.swen3.paperless.correspondent.entity.CorrespondentEntity
import at.fhtw.swen3.paperless.correspondent.model.Correspondent
import at.fhtw.swen3.paperless.correspondent.repository.CorrespondentRepository
import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class CorrespondentServiceImplTest {

    @Mock
    lateinit var repository: CorrespondentRepository

    @InjectMocks
    lateinit var service: CorrespondentServiceImpl

    @Test
    fun create_correspondent_ok() {
        // given
        val correspondent = Correspondent(0, "A1 Telekom", "Provider")
        val saved = CorrespondentEntity(1, correspondent.name, correspondent.notes)
        `when`(repository.findByName(correspondent.name)).thenReturn(null)
        `when`(repository.save(any(CorrespondentEntity::class.java))).thenReturn(saved)

        // when
        val result = service.createCorrespondent(correspondent)

        // then
        assertAll(
            { assertThat(result.id).isEqualTo(1) },
            { assertThat(result.name).isEqualTo(correspondent.name) },
            { assertThat(result.notes).isEqualTo(correspondent.notes) }
        )
    }

    @Test
    fun search_correspondents_ok() {
        // given
        `when`(repository.findAll()).thenReturn(listOf(CorrespondentEntity(1, "A1 Telekom")))

        // when
        val result = service.searchCorrespondents()

        // then
        assertAll(
            { assertThat(result).hasSize(1) },
            { assertThat(result.single().name).isEqualTo("A1 Telekom") }
        )
    }

    @Test
    fun create_duplicate_correspondent_ko() {
        // given
        val correspondent = Correspondent(0, "A1 Telekom", null)
        `when`(repository.findByName(correspondent.name)).thenReturn(CorrespondentEntity(1, correspondent.name))

        // when / then
        assertAll(
            { assertAppError(AppErrorMessage.CORRESPONDENT_NAME_ALREADY_EXISTS) { service.createCorrespondent(correspondent) } },
            { verify(repository, never()).save(any(CorrespondentEntity::class.java)) }
        )
    }

    @Test
    fun get_correspondent_not_found_ko() {
        // given
        `when`(repository.findById(42)).thenReturn(Optional.empty())

        // when / then
        assertAppError(AppErrorMessage.CORRESPONDENT_NOT_FOUND) { service.getCorrespondentById(42) }
    }

    @Test
    fun update_correspondent_ok() {
        // given
        `when`(repository.findById(1)).thenReturn(Optional.of(CorrespondentEntity(1, "Old")))
        `when`(repository.findByName("New")).thenReturn(null)
        `when`(repository.save(any(CorrespondentEntity::class.java))).thenReturn(CorrespondentEntity(1, "New", "Notes"))

        // when
        val result = service.updateCorrespondent(1, Correspondent(99, "New", "Notes"))

        // then
        assertAll(
            { assertThat(result.id).isEqualTo(1) },
            { assertThat(result.name).isEqualTo("New") },
            { assertThat(result.notes).isEqualTo("Notes") }
        )
    }

    @Test
    fun update_duplicate_correspondent_ko() {
        // given
        `when`(repository.findById(1)).thenReturn(Optional.of(CorrespondentEntity(1, "Old")))
        `when`(repository.findByName("Existing")).thenReturn(CorrespondentEntity(2, "Existing"))

        // when / then
        assertAll(
            { assertAppError(AppErrorMessage.CORRESPONDENT_NAME_ALREADY_EXISTS) { service.updateCorrespondent(1, Correspondent(1, "Existing", null)) } },
            { verify(repository, never()).save(any(CorrespondentEntity::class.java)) }
        )
    }

    @Test
    fun delete_correspondent_ok() {
        // given
        val entity = CorrespondentEntity(1, "A1 Telekom")
        `when`(repository.findById(1)).thenReturn(Optional.of(entity))

        // when
        service.deleteCorrespondent(1)

        // then
        verify(repository).delete(entity)
    }

    private fun assertAppError(expected: AppErrorMessage, action: () -> Unit) {
        val thrown = catchThrowable(action)
        assertAll(
            { assertThat(thrown).isInstanceOf(AppException::class.java) },
            { assertThat((thrown as AppException).error).isEqualTo(expected) }
        )
    }
}
