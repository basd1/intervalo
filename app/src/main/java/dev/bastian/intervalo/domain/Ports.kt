package dev.bastian.intervalo.domain

import kotlinx.coroutines.flow.Flow

/** Time sources. Domain logic never reads the system clock directly. */
interface Clock {
    /** Wall-clock time, epoch millis. Only for display and history. */
    fun now(): Long

    /** Monotonic time since boot, including deep sleep. Used for all timing. */
    fun elapsedRealtime(): Long
}

interface RoutineRepository {
    fun observeAll(): Flow<List<Routine>>
    fun observe(id: Long): Flow<Routine?>
    suspend fun get(id: Long): Routine?

    /** Inserts when `routine.id == 0`, otherwise replaces it. Returns the id. */
    suspend fun save(routine: Routine): Long
    suspend fun delete(id: Long)

    /** Returns the id of the copy, or null if the routine does not exist. */
    suspend fun duplicate(id: Long, copyName: String): Long?
    suspend fun count(): Int
}

interface SessionRepository {
    fun observeAll(): Flow<List<Session>>
    suspend fun add(session: Session): Long
    suspend fun clear()
}
