package at.fhtw.swen3.paperless.auth.mapper

import at.fhtw.swen3.paperless.auth.model.LoginCredentials
import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.dto.LoginRequest
import org.mapstruct.InjectionStrategy
import org.mapstruct.Mapper
import org.mapstruct.MappingConstants
import org.mapstruct.ReportingPolicy

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = [MappingConverters::class],
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface AuthMapper {

    fun toCredentials(request: LoginRequest): LoginCredentials
}
