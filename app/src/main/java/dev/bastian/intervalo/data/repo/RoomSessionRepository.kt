package dev.bastian.intervalo.data.repo

import dev.bastian.intervalo.data.db.SessionDao
import dev.bastian.intervalo.data.toDomain
import dev.bastian.intervalo.data.toEntity
import dev.bastian.intervalo.domain.Session
import dev.bastian.intervalo.domain.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSessionRepository(private val dao: SessionDao) : SessionRepository {
    override fun observeAll(): Flow<List<Session>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun add(session: Session): Long = dao.insert(session.toEntity())

    override suspend fun clear() = dao.clear()
}
