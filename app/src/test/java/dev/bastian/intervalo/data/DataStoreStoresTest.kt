package dev.bastian.intervalo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.bastian.intervalo.domain.ActiveWorkout
import dev.bastian.intervalo.domain.Settings
import dev.bastian.intervalo.domain.Step
import dev.bastian.intervalo.domain.StepType
import dev.bastian.intervalo.domain.timer.TimerClockState
import dev.bastian.intervalo.testing.FakeRoutineRepository
import dev.bastian.intervalo.testing.routine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataStoreStoresTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "test.preferences_pb") })
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun activeWorkoutSurvivesARoundTripThroughTheStore() = runBlocking {
        val store = DataStoreActiveWorkoutStore(dataStore)
        val workout = ActiveWorkout(
            routine = routine(
                id = 7,
                name = "Round trip",
                warmup = 30,
                cooldown = 20,
                rounds = 3,
                steps = listOf(Step("Squats", StepType.WORK, 40), Step("", StepType.REST, 15)),
            ),
            startedAtWall = 1_700_000_123_000,
            timer = TimerClockState(startElapsed = 55_000, pausedTotalMs = 4_000, pausedAtElapsed = 70_000, skipOffsetMs = -2_500),
        )

        store.save(workout)

        assertEquals(workout, store.load())
    }

    @Test
    fun clearRemovesTheStoredWorkout() = runBlocking {
        val store = DataStoreActiveWorkoutStore(dataStore)
        store.save(ActiveWorkout(routine(id = 1), 1L, TimerClockState(startElapsed = 1)))

        store.clear()

        assertNull(store.load())
    }

    @Test
    fun unreadableStoredDataIsDiscardedInsteadOfCrashing() = runBlocking {
        dataStore.edit { it[stringPreferencesKey("active_workout")] = "{not json" }
        val store = DataStoreActiveWorkoutStore(dataStore)

        assertNull(store.load())
        assertNull(dataStore.data.first()[stringPreferencesKey("active_workout")])
    }

    @Test
    fun settingsDefaultToEverythingOnAndPersistChanges() = runBlocking {
        val repository = DataStoreSettingsRepository(dataStore)
        assertEquals(Settings(), repository.settings.first())

        repository.update { it.copy(sounds = false, countdown = false) }
        repository.setLastRoutine(5)

        assertEquals(Settings(sounds = false, vibration = true, countdown = false, keepScreenOn = true), repository.settings.first())
        assertEquals(5L, repository.lastRoutineId.first())
    }

    @Test
    fun sampleRoutinesAreInstalledOnlyOnce() = runBlocking {
        val routines = FakeRoutineRepository()
        val seeder = SampleSeeder(routines, dataStore)

        seeder.seedIfNeeded()
        assertEquals(3, routines.count())

        routines.saved.forEach { routines.delete(it.id) }
        seeder.seedIfNeeded()
        assertEquals(0, routines.count())
    }
}
