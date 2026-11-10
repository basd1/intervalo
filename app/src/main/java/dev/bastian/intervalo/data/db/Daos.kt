package dev.bastian.intervalo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines ORDER BY id")
    abstract fun observeAll(): Flow<List<RoutineWithSteps>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    abstract fun observe(id: Long): Flow<RoutineWithSteps?>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    abstract suspend fun get(id: Long): RoutineWithSteps?

    @Query("SELECT COUNT(*) FROM routines")
    abstract suspend fun count(): Int

    @Insert
    protected abstract suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    protected abstract suspend fun updateRoutine(routine: RoutineEntity)

    @Insert
    protected abstract suspend fun insertSteps(steps: List<StepEntity>)

    @Query("DELETE FROM steps WHERE routineId = :routineId")
    protected abstract suspend fun deleteSteps(routineId: Long)

    @Query("DELETE FROM routines WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /** Writes the routine and replaces all its steps in one transaction. Returns the routine id. */
    @Transaction
    open suspend fun upsert(routine: RoutineEntity, steps: List<StepEntity>): Long {
        val id = if (routine.id == 0L) {
            insertRoutine(routine)
        } else {
            updateRoutine(routine)
            deleteSteps(routine.id)
            routine.id
        }
        insertSteps(steps.map { it.copy(routineId = id) })
        return id
    }
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("DELETE FROM sessions")
    suspend fun clear()
}
