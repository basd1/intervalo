package dev.bastian.intervalo.widget

import dev.bastian.intervalo.domain.Clock
import dev.bastian.intervalo.domain.Routine
import dev.bastian.intervalo.domain.RoutineRepository
import dev.bastian.intervalo.domain.SettingsRepository
import dev.bastian.intervalo.domain.WorkoutState
import dev.bastian.intervalo.domain.timer.Phase
import dev.bastian.intervalo.domain.timer.WorkoutRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map

/** What the home screen widget shows. Computed from the workout state so it can be tested without Glance. */
sealed interface WidgetModel {
    /** No workout running: offer to start the last routine used. Both fields are null before any was started. */
    data class Idle(val routineId: Long?, val routineName: String?) : WidgetModel

    data class Active(
        val routineName: String,
        val phase: Phase,
        val paused: Boolean,
        /** Wall-clock time the workout is expected to end, or null while paused (it is not known then). */
        val endsAtWall: Long?,
    ) : WidgetModel
}

/**
 * Emits a new [WidgetModel] only when something the widget displays changes: a workout starts,
 * the phase changes, it is paused or resumed, it ends, or the last routine changes. The ticking
 * remaining time is deliberately not part of it, so the widget is not redrawn every second.
 */
class WidgetModels(
    private val runner: WorkoutRunner,
    private val routines: RoutineRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    fun observe(): Flow<WidgetModel> = combine(
        runner.state.distinctUntilChangedBy(::signature),
        settings.lastRoutineId.map { id -> id?.let { routines.get(it) } },
    ) { state, lastRoutine -> widgetModel(state, lastRoutine, clock.now()) }
        .distinctUntilChanged()

    private fun signature(state: WorkoutState): List<Any> = when (state) {
        is WorkoutState.Active -> listOf("active", state.routine.id, state.timer.phaseIndex, state.timer.isPaused)
        else -> listOf("idle")
    }
}

fun widgetModel(state: WorkoutState, lastRoutine: Routine?, nowWall: Long): WidgetModel = when (state) {
    is WorkoutState.Active -> WidgetModel.Active(
        routineName = state.routine.name,
        phase = state.timer.phase,
        paused = state.timer.isPaused,
        endsAtWall = if (state.timer.isPaused) null else nowWall + state.timer.totalRemainingMs,
    )
    else -> WidgetModel.Idle(lastRoutine?.id, lastRoutine?.name)
}
