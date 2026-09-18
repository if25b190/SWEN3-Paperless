package at.fhtw.swen3.paperless.team.repository

import at.fhtw.swen3.paperless.team.entity.TeamEntity
import org.springframework.data.jpa.repository.JpaRepository

interface TeamRepository : JpaRepository<TeamEntity, Long>