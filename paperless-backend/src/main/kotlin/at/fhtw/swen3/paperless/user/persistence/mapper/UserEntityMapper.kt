package at.fhtw.swen3.paperless.user.persistence.mapper

import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.user.entity.UserEntity
import at.fhtw.swen3.paperless.user.model.User
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
interface UserEntityMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = ["requireNotNull"])
    fun toModel(entity: UserEntity): User

    fun toEntity(user: User): UserEntity =
        UserEntity(
            id = user.id,
            username = user.username,
            password = user.password,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
            version = user.version
        )
}
