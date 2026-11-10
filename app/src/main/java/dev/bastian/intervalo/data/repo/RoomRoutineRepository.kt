package dev.bastian.intervalo.data.repo

import dev.bastian.intervalo.data.db.RoutineDao
import dev.bastian.intervalo.data.toDomain
import dev.bastian.intervalo.data.toEntity
import dev.bastian.intervalo.data.toStepEntities
import dev.bastian.intervalo.domain.Routine
import dev.bastian.intervalo.domain.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomRoutineRepository(private val dao: RoutineDao) : RoutineRepository {
    override fun observeAll(): Flow<List<Routine>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observe(id: Long): Flow<Routine?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun get(id: Long): Routine? = dao.get(id)?.toDomain()

    override suspend fun save(routine: Routine): Long = dao.upsert(routine.toEntity(), routine.toStepEntities())

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun duplicate(id: Long, copyName: String): Long? {
        val original = get(id) ?: return null
        return save(original.copy(id = 0, name = copyName))
    }

    override suspend fun count(): Int = dao.count()
}
