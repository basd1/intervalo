package dev.bastian.intervalo.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.intervalo.domain.SettingsRepository
import dev.bastian.intervalo.domain.WorkoutState
import dev.bastian.intervalo.domain.timer.WorkoutRunner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WorkoutUiState(
    val workout: WorkoutState = WorkoutState.Idle,
    val keepScreenOn: Boolean = true,
)

/** Thin layer between the screen and the [WorkoutRunner] the service keeps alive. */
class WorkoutViewModel(
    private val runner: WorkoutRunner,
    settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<WorkoutUiState> = combine(runner.state, settings.settings) { workout, current ->
        WorkoutUiState(workout, current.keepScreenOn)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState(runner.state.value))

    fun togglePause() = runner.togglePause()
    fun skipNext() = runner.skipNext()
    fun skipPrevious() = runner.skipPrevious()
    fun stop() = runner.stop()
    fun dismissResult() = runner.dismissResult()
}
