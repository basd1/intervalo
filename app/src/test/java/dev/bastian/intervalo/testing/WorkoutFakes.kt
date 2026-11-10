package dev.bastian.intervalo.testing

import dev.bastian.intervalo.domain.ActiveWorkout
import dev.bastian.intervalo.domain.ActiveWorkoutStore
import dev.bastian.intervalo.domain.PhaseFeedback
import dev.bastian.intervalo.domain.Settings
import dev.bastian.intervalo.domain.SettingsRepository
import dev.bastian.intervalo.domain.timer.PhaseKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    val current = MutableStateFlow(initial)
    private val last = MutableStateFlow<Long?>(null)

    override val settings: Flow<Settings> = current
    override val lastRoutineId: Flow<Long?> = last.map { it }

    override suspend fun update(transform: (Settings) -> Settings) {
        current.value = transform(current.value)
    }

    override suspend fun setLastRoutine(id: Long) {
        last.value = id
    }
}

class FakeActiveWorkoutStore : ActiveWorkoutStore {
    var stored: ActiveWorkout? = null
    var saves = 0

    override suspend fun save(workout: ActiveWorkout) {
        stored = workout
        saves++
    }

    override suspend fun load(): ActiveWorkout? = stored

    override suspend fun clear() {
        stored = null
    }
}

class FakePhaseFeedback : PhaseFeedback {
    val events = mutableListOf<String>()
    val settingsSeen = mutableListOf<Settings>()

    override fun phaseStarted(kind: PhaseKind, settings: Settings) {
        events += "phase:${kind.name}"
        settingsSeen += settings
    }

    override fun countdownTick(settings: Settings) {
        events += "tick"
        settingsSeen += settings
    }

    override fun finished(settings: Settings) {
        events += "finished"
        settingsSeen += settings
    }
}
