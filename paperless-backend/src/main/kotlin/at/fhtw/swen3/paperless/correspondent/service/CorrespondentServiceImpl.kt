package at.fhtw.swen3.paperless.correspondent.service

import at.fhtw.swen3.paperless.correspondent.mapper.CorrespondentEntityMapper
import at.fhtw.swen3.paperless.correspondent.model.Correspondent
import at.fhtw.swen3.paperless.correspondent.repository.CorrespondentRepository
import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CorrespondentServiceImpl(
    private val repository: CorrespondentRepository
) : CorrespondentService {

    @Transactional(readOnly = true)
    override fun searchCorrespondents(): List<Correspondent> =
        repository.findAll().map(CorrespondentEntityMapper::toModel)

    override fun createCorrespondent(correspondent: Correspondent): Correspondent {
        if (repository.findByName(correspondent.name) != null) {
            throw AppException(AppErrorMessage.CORRESPONDENT_NAME_ALREADY_EXISTS)
        }

        val saved = repository.save(CorrespondentEntityMapper.toEntity(correspondent))
        return CorrespondentEntityMapper.toModel(saved)
    }

    @Transactional(readOnly = true)
    override fun getCorrespondentById(id: Long): Correspondent =
        CorrespondentEntityMapper.toModel(findEntity(id))

    override fun updateCorrespondent(id: Long, correspondent: Correspondent): Correspondent {
        val current = findEntity(id)
        val duplicate = repository.findByName(correspondent.name)
        if (duplicate != null && duplicate.id != current.id) {
            throw AppException(AppErrorMessage.CORRESPONDENT_NAME_ALREADY_EXISTS)
        }

        val updated = correspondent.copy(id = current.id)
        val saved = repository.save(CorrespondentEntityMapper.toEntity(updated))
        return CorrespondentEntityMapper.toModel(saved)
    }

    override fun deleteCorrespondent(id: Long) {
        repository.delete(findEntity(id))
    }

    private fun findEntity(id: Long) =
        repository.findById(id).orElseThrow {
            AppException(AppErrorMessage.CORRESPONDENT_NOT_FOUND)
        }
}
