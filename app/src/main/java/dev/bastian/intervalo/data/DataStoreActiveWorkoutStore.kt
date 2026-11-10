package dev.bastian.intervalo.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.bastian.intervalo.domain.ActiveWorkout
import dev.bastian.intervalo.domain.ActiveWorkoutStore
import dev.bastian.intervalo.domain.Routine
import dev.bastian.intervalo.domain.Step
import dev.bastian.intervalo.domain.StepType
import dev.bastian.intervalo.domain.timer.TimerClockState
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Keeps the running workout as one JSON value in DataStore. */
class DataStoreActiveWorkoutStore(private val store: DataStore<Preferences>) : ActiveWorkoutStore {

    override suspend fun save(workout: ActiveWorkout) {
        val json = Json.encodeToString(StoredWorkout.serializer(), workout.toStored())
        store.edit { it[KEY] = json }
    }

    override suspend fun load(): ActiveWorkout? {
        val json = store.data.first()[KEY] ?: return null
        return try {
            Json.decodeFromString(StoredWorkout.serializer(), json).toDomain()
        } catch (e: IllegalArgumentException) {
            // Covers SerializationException (a subclass) and an unknown step type.
            // Unreadable data (older format): drop it rather than crash on every launch.
            Log.w(TAG, "Discarding unreadable stored workout", e)
            clear()
            null
        }
    }

    override suspend fun clear() {
        store.edit { it.remove(KEY) }
    }

    private companion object {
        const val TAG = "ActiveWorkoutStore"
        val KEY = stringPreferencesKey("active_workout")
    }
}

@Serializable
private data class StoredStep(val name: String, val type: String, val durationSeconds: Int)

@Serializable
private data class StoredWorkout(
    val routineId: Long,
    val routineName: String,
    val color: Int,
    val warmupSeconds: Int,
    val cooldownSeconds: Int,
    val rounds: Int,
    val steps: List<StoredStep>,
    val startedAtWall: Long,
    val startElapsed: Long,
    val pausedTotalMs: Long,
    val pausedAtElapsed: Long?,
    val skipOffsetMs: Long,
)

private fun ActiveWorkout.toStored() = StoredWorkout(
    routineId = routine.id,
    routineName = routine.name,
    color = routine.color,
    warmupSeconds = routine.warmupSeconds,
    cooldownSeconds = routine.cooldownSeconds,
    rounds = routine.rounds,
    steps = routine.steps.map { StoredStep(it.name, it.type.name, it.durationSeconds) },
    startedAtWall = startedAtWall,
    startElapsed = timer.startElapsed,
    pausedTotalMs = timer.pausedTotalMs,
    pausedAtElapsed = timer.pausedAtElapsed,
    skipOffsetMs = timer.skipOffsetMs,
)

private fun StoredWorkout.toDomain() = ActiveWorkout(
    routine = Routine(
        id = routineId,
        name = routineName,
        color = color,
        warmupSeconds = warmupSeconds,
        cooldownSeconds = cooldownSeconds,
        rounds = rounds,
        steps = steps.map { Step(it.name, StepType.valueOf(it.type), it.durationSeconds) },
    ),
    startedAtWall = startedAtWall,
    timer = TimerClockState(startElapsed, pausedTotalMs, pausedAtElapsed, skipOffsetMs),
)
