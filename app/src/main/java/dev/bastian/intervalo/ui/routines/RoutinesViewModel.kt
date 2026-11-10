package dev.bastian.intervalo.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.intervalo.domain.Routine
import dev.bastian.intervalo.domain.RoutineRepository
import dev.bastian.intervalo.domain.WorkoutState
import dev.bastian.intervalo.domain.timer.WorkoutRunner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutinesUiState(
    val isLoaded: Boolean = false,
    val routines: List<Routine> = emptyList(),
    /** Name of the routine being run right now, if any. */
    val activeRoutineName: String? = null,
)

class RoutinesViewModel(
    private val repository: RoutineRepository,
    runner: WorkoutRunner,
) : ViewModel() {

    val uiState: StateFlow<RoutinesUiState> = combine(repository.observeAll(), runner.state) { routines, workout ->
        RoutinesUiState(
            isLoaded = true,
            routines = routines,
            activeRoutineName = (workout as? WorkoutState.Active)?.routine?.name,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUiState())

    /** The notification permission is asked about once per process, not on every workout start. */
    var notificationPromptShown = false
        private set

    fun markNotificationPromptShown() {
        notificationPromptShown = true
    }

    fun duplicate(routine: Routine, copyName: String) {
        viewModelScope.launch { repository.duplicate(routine.id, copyName) }
    }

    fun delete(routine: Routine) {
        viewModelScope.launch { repository.delete(routine.id) }
    }
}
