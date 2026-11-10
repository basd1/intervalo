package dev.bastian.intervalo.ui

import dev.bastian.intervalo.domain.timer.WorkoutRunner
import dev.bastian.intervalo.testing.FakeActiveWorkoutStore
import dev.bastian.intervalo.testing.FakeClock
import dev.bastian.intervalo.testing.FakePhaseFeedback
import dev.bastian.intervalo.testing.FakeRoutineRepository
import dev.bastian.intervalo.testing.FakeSessionRepository
import dev.bastian.intervalo.testing.FakeSettingsRepository
import dev.bastian.intervalo.testing.MainDispatcherRule
import dev.bastian.intervalo.testing.routine
import dev.bastian.intervalo.ui.routines.RoutinesViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutinesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRoutineRepository()

    @Test
    fun listsRoutinesDuplicatesAndDeletes() = runTest {
        val id = repository.save(routine(name = "A"))
        val runner = WorkoutRunner(repository, FakeSessionRepository(), FakeActiveWorkoutStore(), FakeSettingsRepository(), FakeClock(), FakePhaseFeedback(), backgroundScope)
        val viewModel by lazy { RoutinesViewModel(repository, runner) }

        val loaded = viewModel.uiState.first { it.isLoaded }
        assertEquals(listOf("A"), loaded.routines.map { it.name })
        assertNull(loaded.activeRoutineName)

        viewModel.duplicate(loaded.routines.single(), "A (copy)")
        assertEquals(listOf("A", "A (copy)"), viewModel.uiState.first { it.routines.size == 2 }.routines.map { it.name })

        viewModel.delete(repository.get(id)!!)
        assertEquals(listOf("A (copy)"), viewModel.uiState.first { it.routines.size == 1 }.routines.map { it.name })
    }

    @Test
    fun showsTheRunningRoutineAndAsksForNotificationsOnlyOnce() = runTest {
        val id = repository.save(routine(name = "Running"))
        val runner = WorkoutRunner(repository, FakeSessionRepository(), FakeActiveWorkoutStore(), FakeSettingsRepository(), FakeClock(), FakePhaseFeedback(), backgroundScope)
        val viewModel by lazy { RoutinesViewModel(repository, runner) }
        runner.start(id)

        assertEquals("Running", viewModel.uiState.first { it.activeRoutineName != null }.activeRoutineName)

        assertFalse(viewModel.notificationPromptShown)
        viewModel.markNotificationPromptShown()
        assertTrue(viewModel.notificationPromptShown)
    }
}
