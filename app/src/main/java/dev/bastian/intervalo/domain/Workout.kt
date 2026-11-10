package dev.bastian.intervalo.domain

import dev.bastian.intervalo.domain.timer.PhaseKind
import dev.bastian.intervalo.domain.timer.TimerClockState
import dev.bastian.intervalo.domain.timer.TimerState
import kotlinx.coroutines.flow.Flow

data class Settings(
    val sounds: Boolean = true,
    val vibration: Boolean = true,
    /** Beeps on the last three seconds of every phase. */
    val countdown: Boolean = true,
    val keepScreenOn: Boolean = true,
)

interface SettingsRepository {
    val settings: Flow<Settings>

    /** The routine started most recently, or null if none was started yet. */
    val lastRoutineId: Flow<Long?>
    suspend fun update(transform: (Settings) -> Settings)
    suspend fun setLastRoutine(id: Long)
}

/** What is needed to bring a running workout back after the process died. */
data class ActiveWorkout(
    /** Copy of the routine taken when the workout started, so later edits do not change it. */
    val routine: Routine,
    val startedAtWall: Long,
    val timer: TimerClockState,
)

interface ActiveWorkoutStore {
    suspend fun save(workout: ActiveWorkout)
    suspend fun load(): ActiveWorkout?
    suspend fun clear()
}

/** Sound and vibration cues. Implementations decide how, honouring the given settings. */
interface PhaseFeedback {
    fun phaseStarted(kind: PhaseKind, settings: Settings)
    fun countdownTick(settings: Settings)
    fun finished(settings: Settings)
}

sealed interface WorkoutState {
    data object Idle : WorkoutState

    data class Active(
        val routine: Routine,
        val timer: TimerState,
        val startedAtWall: Long,
    ) : WorkoutState

    /** The workout ran to its end. Stays until the user dismisses the summary. */
    data class Finished(val routine: Routine, val activeSeconds: Int) : WorkoutState
}
