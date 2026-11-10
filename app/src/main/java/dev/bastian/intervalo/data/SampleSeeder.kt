package dev.bastian.intervalo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.bastian.intervalo.domain.RoutineRepository
import dev.bastian.intervalo.domain.SampleRoutines
import kotlinx.coroutines.flow.first

/**
 * Installs the sample routines once. A flag in DataStore (not "the table is empty") decides,
 * so a user who deletes every routine does not get them back on the next launch.
 */
class SampleSeeder(
    private val routines: RoutineRepository,
    private val store: DataStore<Preferences>,
) {
    suspend fun seedIfNeeded() {
        if (store.data.first()[SEEDED] == true) return
        if (routines.count() == 0) {
            SampleRoutines.all.forEach { routines.save(it) }
        }
        store.edit { it[SEEDED] = true }
    }

    private companion object {
        val SEEDED = booleanPreferencesKey("samples_seeded")
    }
}
