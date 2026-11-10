package dev.bastian.intervalo.ui

import dev.bastian.intervalo.domain.Session
import dev.bastian.intervalo.domain.Settings
import dev.bastian.intervalo.testing.FakeClock
import dev.bastian.intervalo.testing.FakeSessionRepository
import dev.bastian.intervalo.testing.FakeSettingsRepository
import dev.bastian.intervalo.testing.MainDispatcherRule
import dev.bastian.intervalo.ui.history.HistoryViewModel
import dev.bastian.intervalo.ui.settings.SettingsViewModel
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryAndSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeClock()
    private val zone = ZoneId.of("UTC")

    private fun session(offsetDays: Long, seconds: Int, completed: Boolean = true) = Session(
        routineId = 1,
        routineName = "R",
        startedAt = clock.now() - offsetDays * 86_400_000L,
        durationSeconds = seconds,
        completed = completed,
    )

    @Test
    fun historyListsSessionsNewestFirstWithTheWeekSummary() = runTest {
        val sessions = FakeSessionRepository()
        sessions.add(session(offsetDays = 2, seconds = 600))
        sessions.add(session(offsetDays = 0, seconds = 300, completed = false))
        val viewModel by lazy { HistoryViewModel(sessions, clock, zone) }

        val state = viewModel.uiState.first { it.isLoaded }

        assertEquals(listOf(300, 600), state.sessions.map { it.durationSeconds })
        assertEquals(2, state.week!!.totalSessions)
        assertEquals(900, state.week!!.totalSeconds)
    }

    @Test
    fun anEmptyHistoryIsLoadedButEmpty() = runTest {
        val viewModel by lazy { HistoryViewModel(FakeSessionRepository(), clock, zone) }

        val state = viewModel.uiState.first { it.isLoaded }

        assertTrue(state.sessions.isEmpty())
        assertEquals(0, state.week!!.totalSessions)
    }

    @Test
    fun clearingEmptiesTheHistory() = runTest {
        val sessions = FakeSessionRepository()
        sessions.add(session(0, 100))
        val viewModel by lazy { HistoryViewModel(sessions, clock, zone) }
        viewModel.uiState.first { it.sessions.isNotEmpty() }

        viewModel.clear()

        assertTrue(viewModel.uiState.first { it.isLoaded && it.sessions.isEmpty() }.sessions.isEmpty())
    }

    @Test
    fun eachSettingIsSavedIndependently() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel by lazy { SettingsViewModel(repository) }
        assertEquals(Settings(), viewModel.settings.first())

        viewModel.setSounds(false)
        viewModel.setCountdown(false)
        viewModel.setKeepScreenOn(false)
        viewModel.setVibration(false)

        assertEquals(Settings(sounds = false, vibration = false, countdown = false, keepScreenOn = false), repository.current.value)
        viewModel.setSounds(true)
        assertTrue(repository.current.value.sounds)
        assertFalse(repository.current.value.vibration)
    }
}
