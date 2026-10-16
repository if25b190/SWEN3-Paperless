package at.fhtw.swen3.paperless.user.mapper

import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.dto.CreateUserRequest
import at.fhtw.swen3.paperless.dto.UserResponse
import at.fhtw.swen3.paperless.user.model.User
import java.time.Instant
import org.mapstruct.InjectionStrategy
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingConstants
import org.mapstruct.ReportingPolicy

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = [MappingConverters::class],
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface UserMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = ["requireNotNull"])
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = ["toOffset"])
    @Mapping(target = "updatedAt", source = "updatedAt", qualifiedByName = ["toOffset"])
    fun toDto(user: User): UserResponse

    fun fromCreateDto(dto: CreateUserRequest, now: Instant): User =
        User(
            id = null,
            username = dto.username,
            password = dto.password,
            createdAt = now,
            updatedAt = null
        )
}
