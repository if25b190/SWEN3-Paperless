package at.fhtw.swen3.paperless.common.mapper

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.mapstruct.Named
import org.springframework.stereotype.Component

@Component
class MappingConverters {

    @Named("toOffset")
    fun toOffset(instant: Instant?): OffsetDateTime? = instant?.atOffset(ZoneOffset.UTC)

    @Named("requireNotNull")
    fun requireNotNull(value: UUID?): UUID = value ?: throw IllegalStateException("null id")
}
