package dev.bastian.intervalo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.intervalo.domain.Settings
import dev.bastian.intervalo.domain.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<Settings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun setSounds(enabled: Boolean) = update { it.copy(sounds = enabled) }
    fun setVibration(enabled: Boolean) = update { it.copy(vibration = enabled) }
    fun setCountdown(enabled: Boolean) = update { it.copy(countdown = enabled) }
    fun setKeepScreenOn(enabled: Boolean) = update { it.copy(keepScreenOn = enabled) }

    private fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
