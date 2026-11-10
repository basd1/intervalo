package dev.bastian.intervalo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import dev.bastian.intervalo.domain.Settings
import dev.bastian.intervalo.domain.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {

    override val settings: Flow<Settings> = store.data.map { it.toSettings() }

    override val lastRoutineId: Flow<Long?> = store.data.map { it[LAST_ROUTINE] }

    override suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[SOUNDS] = next.sounds
            prefs[VIBRATION] = next.vibration
            prefs[COUNTDOWN] = next.countdown
            prefs[KEEP_SCREEN_ON] = next.keepScreenOn
        }
    }

    override suspend fun setLastRoutine(id: Long) {
        store.edit { it[LAST_ROUTINE] = id }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            sounds = this[SOUNDS] ?: defaults.sounds,
            vibration = this[VIBRATION] ?: defaults.vibration,
            countdown = this[COUNTDOWN] ?: defaults.countdown,
            keepScreenOn = this[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
        )
    }

    private companion object {
        val SOUNDS = booleanPreferencesKey("settings_sounds")
        val VIBRATION = booleanPreferencesKey("settings_vibration")
        val COUNTDOWN = booleanPreferencesKey("settings_countdown")
        val KEEP_SCREEN_ON = booleanPreferencesKey("settings_keep_screen_on")
        val LAST_ROUTINE = longPreferencesKey("last_routine_id")
    }
}
