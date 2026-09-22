package at.fhtw.swen3.paperless.correspondent.service

import at.fhtw.swen3.paperless.correspondent.model.Correspondent

interface CorrespondentService {

    fun searchCorrespondents(): List<Correspondent>

    fun createCorrespondent(correspondent: Correspondent): Correspondent

    fun getCorrespondentById(id: Long): Correspondent

    fun updateCorrespondent(id: Long, correspondent: Correspondent): Correspondent

    fun deleteCorrespondent(id: Long)
}
