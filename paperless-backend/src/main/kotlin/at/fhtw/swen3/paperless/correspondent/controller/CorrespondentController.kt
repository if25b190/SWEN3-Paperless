package at.fhtw.swen3.paperless.correspondent.controller

import at.fhtw.swen3.paperless.api.CorrespondentsApi
import at.fhtw.swen3.paperless.correspondent.mapper.CorrespondentMapper
import at.fhtw.swen3.paperless.correspondent.service.CorrespondentService
import at.fhtw.swen3.paperless.dto.CorrespondentListResponse
import at.fhtw.swen3.paperless.dto.CorrespondentResponse
import at.fhtw.swen3.paperless.dto.CreateCorrespondentRequest
import at.fhtw.swen3.paperless.dto.UpdateCorrespondentRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
class CorrespondentController(
    private val service: CorrespondentService
) : CorrespondentsApi {

    override fun searchCorrespondents(): ResponseEntity<CorrespondentListResponse> {
        val correspondents = service.searchCorrespondents()
        val items = correspondents.map(CorrespondentMapper::toDto)
        return ResponseEntity.ok(CorrespondentListResponse(items = items))
    }

    override fun createCorrespondent(
        createCorrespondentRequest: CreateCorrespondentRequest
    ): ResponseEntity<CorrespondentResponse> {
        val correspondent = CorrespondentMapper.fromCreateDto(createCorrespondentRequest)
        val created = service.createCorrespondent(correspondent)
        val response = CorrespondentMapper.toDto(created)
        return ResponseEntity.created(URI.create("/correspondents/${created.id}")).body(response)
    }

    override fun getCorrespondentById(id: Long): ResponseEntity<CorrespondentResponse> {
        val correspondent = service.getCorrespondentById(id)
        val response = CorrespondentMapper.toDto(correspondent)
        return ResponseEntity.ok(response)
    }

    override fun updateCorrespondent(
        id: Long,
        updateCorrespondentRequest: UpdateCorrespondentRequest
    ): ResponseEntity<CorrespondentResponse> {
        val current = service.getCorrespondentById(id)
        val correspondent = CorrespondentMapper.applyUpdate(current, updateCorrespondentRequest)
        val updated = service.updateCorrespondent(id, correspondent)
        val response = CorrespondentMapper.toDto(updated)
        return ResponseEntity.ok(response)
    }

    override fun deleteCorrespondent(id: Long): ResponseEntity<Unit> {
        service.deleteCorrespondent(id)
        return ResponseEntity.noContent().build()
    }
}
