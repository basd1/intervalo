package dev.bastian.intervalo.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.intervalo.domain.Routine
import dev.bastian.intervalo.domain.RoutineRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutinesUiState(
    val isLoaded: Boolean = false,
    val routines: List<Routine> = emptyList(),
)

class RoutinesViewModel(private val repository: RoutineRepository) : ViewModel() {

    val uiState: StateFlow<RoutinesUiState> = repository.observeAll()
        .map { RoutinesUiState(isLoaded = true, routines = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUiState())

    fun duplicate(routine: Routine, copyName: String) {
        viewModelScope.launch { repository.duplicate(routine.id, copyName) }
    }

    fun delete(routine: Routine) {
        viewModelScope.launch { repository.delete(routine.id) }
    }
}
