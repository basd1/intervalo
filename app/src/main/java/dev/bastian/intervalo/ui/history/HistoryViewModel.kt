package dev.bastian.intervalo.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.intervalo.domain.Clock
import dev.bastian.intervalo.domain.Session
import dev.bastian.intervalo.domain.SessionRepository
import dev.bastian.intervalo.domain.WeeklySummary
import dev.bastian.intervalo.domain.weeklySummary
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val isLoaded: Boolean = false,
    val sessions: List<Session> = emptyList(),
    val week: WeeklySummary? = null,
)

class HistoryViewModel(
    private val repository: SessionRepository,
    clock: Clock,
    zone: ZoneId,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = repository.observeAll()
        .map { sessions ->
            HistoryUiState(
                isLoaded = true,
                sessions = sessions,
                week = weeklySummary(sessions, clock.now(), zone),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }
}
