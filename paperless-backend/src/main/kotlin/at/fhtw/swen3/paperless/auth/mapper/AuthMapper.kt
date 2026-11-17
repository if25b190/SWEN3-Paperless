package at.fhtw.swen3.paperless.auth.mapper

import at.fhtw.swen3.paperless.auth.model.LoginCredentials
import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.dto.LoginRequest
import at.fhtw.swen3.paperless.exception.InvalidCredentialsException
import java.util.Base64
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

    fun toCredentials(request: LoginRequest): LoginCredentials {
        val password = try {
            String(Base64.getDecoder().decode(request.password), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            throw InvalidCredentialsException()
        }
        return LoginCredentials(request.username, password)
    }
}
