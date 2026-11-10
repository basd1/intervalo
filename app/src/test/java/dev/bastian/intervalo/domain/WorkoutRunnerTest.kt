package dev.bastian.intervalo.domain

import dev.bastian.intervalo.domain.timer.PhaseKind
import dev.bastian.intervalo.domain.timer.WorkoutRunner
import dev.bastian.intervalo.testing.FakeActiveWorkoutStore
import dev.bastian.intervalo.testing.FakeClock
import dev.bastian.intervalo.testing.FakePhaseFeedback
import dev.bastian.intervalo.testing.FakeRoutineRepository
import dev.bastian.intervalo.testing.FakeSessionRepository
import dev.bastian.intervalo.testing.FakeSettingsRepository
import dev.bastian.intervalo.testing.routine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutRunnerTest {

    private val clock = FakeClock()
    private val routines = FakeRoutineRepository()
    private val sessions = FakeSessionRepository()
    private val store = FakeActiveWorkoutStore()
    private val feedback = FakePhaseFeedback()
    private val settings = FakeSettingsRepository()

    // Warm-up 10s, 2 x (work 20s, rest 10s), cool-down 5s = 75s.
    private val sample = routine(
        name = "Sample",
        warmup = 10,
        cooldown = 5,
        rounds = 2,
        steps = listOf(Step("", StepType.WORK, 20), Step("", StepType.REST, 10)),
    )

    private fun runner(scope: CoroutineScope, theClock: FakeClock = clock) = WorkoutRunner(
        routines, sessions, store, settings, theClock, feedback, scope,
    )

    private suspend fun savedRoutineId() = routines.save(sample)

    private fun TestScope.newRunner(theClock: FakeClock = clock) = runner(backgroundScope, theClock)

    // --- running ---------------------------------------------------------------------------------

    @Test
    fun startingPublishesAnActiveStateAndPersistsIt() = runTest(UnconfinedTestDispatcher()) {
        val runner = newRunner()
        val id = savedRoutineId()

        assertTrue(runner.start(id))

        val state = runner.state.value as WorkoutState.Active
        assertEquals("Sample", state.routine.name)
        assertEquals(PhaseKind.WARMUP, state.timer.phase.kind)
        assertNotNull(store.stored)
        assertEquals(listOf("phase:WARMUP"), feedback.events)
    }

    @Test
    fun startingAnUnknownRoutineFails() = runTest(UnconfinedTestDispatcher()) {
        val runner = newRunner()

        assertFalse(runner.start(99))

        assertEquals(WorkoutState.Idle, runner.state.value)
        assertNull(store.stored)
    }

    @Test
    fun startingWhileRunningKeepsTheCurrentWorkout() = runTest(UnconfinedTestDispatcher()) {
        val runner = newRunner()
        val id = savedRoutineId()
        runner.start(id)
        clock.advance(5_000)

        assertTrue(runner.start(id))
        runner.tick()

        assertEquals(5_000L, (runner.state.value as WorkoutState.Active).timer.phaseElapsedMs)
    }

    @Test
    fun aPhaseChangeFiresOneCue() = runTest(UnconfinedTestDispatcher()) {
        settings.update { it.copy(countdown = false) }
        val runner = newRunner()
        runner.start(savedRoutineId())

        clock.advance(10_000)
        runner.tick()
        runner.tick() // a second tick in the same phase must not repeat the cue

        assertEquals(listOf("phase:WARMUP", "phase:WORK"), feedback.events)
    }

    @Test
    fun countdownBeepsOnceForEachOfTheLastThreeSeconds() = runTest(UnconfinedTestDispatcher()) {
        val runner = newRunner()
        runner.start(savedRoutineId())

        repeat(100) { // 10 seconds in 100 ms steps; ticks within the same second must not repeat
            clock.advance(100)
            runner.tick()
        }

        // First phase starts, then 3-2-1 before the boundary; the new phase starts exactly at 10s.
        assertEquals(listOf("phase:WARMUP", "tick", "tick", "tick", "phase:WORK"), feedback.events)
    }

    @Test
    fun noCountdownBeepsWhenPaused() = runTest(UnconfinedTestDispatcher()) {
        val runner = newRunner()
        runner.start(savedRoutineId())
        clock.advance(8_000)
        runner.pause()
        feedback.events.clear()

        clock.advance(60_000)
        runner.tick()

        assertTrue(feedback.events.isEmpty())
    }

    @Test
    fun cuesCarryTheCurrentSettings() = runTest(UnconfinedTestDispatcher()) {
        settings.update { it.copy(sounds = false, vibration = true) }
        val runner = newRunner()

        runner.start(savedRoutineId())

        assertFalse(feedback.settingsSeen.single().sounds)
        assertTrue(feedback.settingsSeen.single().vibration)
    }

    @Test
    fun togglePauseFreezesAndResumes() = runTest(UnconfinedTestDispatcher()) {
        val runner = newRunner()
        runner.start(savedRoutineId())
        clock.advance(4_000)

        runner.togglePause()
        clock.advance(120_000)
        runner.tick()
        val paused = runner.state.value as WorkoutState.Active
        assertTrue(paused.timer.isPaused)
        assertEquals(6_000L, paused.timer.phaseRemainingMs)
        assertTrue(store.stored!!.timer.pausedAtElapsed != null)

        runner.togglePause()
        val resumed = runner.state.value as WorkoutState.Active
        assertFalse(resumed.timer.isPaused)
        assertEquals(6_000L, resumed.timer.phaseRemainingMs)
        assertNull(store.stored!!.timer.pausedAtElapsed)
    }

    @Test
    fun skipAndPreviousMoveBetweenPhases() = runTest(UnconfinedTestDispatcher()) {
        val runner = newRunner()
        runner.start(savedRoutineId())

        runner.skipNext()
        assertEquals(PhaseKind.WORK, (runner.state.value as WorkoutState.Active).timer.phase.kind)

        clock.advance(500)
        runner.skipPrevious()
        assertEquals(PhaseKind.WARMUP, (runner.state.value as WorkoutState.Active).timer.phase.kind)
    }

    // --- ending ----------------------------------------------------------------------------------

    @Test
    fun runningToTheEndSavesACompletedSessionAndClearsTheStore() = runTest(UnconfinedTestDispatcher()) {
        val runner = newRunner()
        val id = savedRoutineId()
        val startWall = clock.now()
        runner.start(id)

        clock.advance(75_000)
        runner.tick()

        val finished = runner.state.value as WorkoutState.Finished
        assertEquals(75, finished.activeSeconds)
        assertEquals("finished", feedback.events.last())
        val session = sessions.saved.single()
        assertEquals(id, session.routineId)
        assertEquals("Sample", session.routineName)
        assertEquals(startWall, session.startedAt)
        assertEquals(75, session.durationSeconds)
        assertTrue(session.completed)
        assertNull(store.stored)

        runner.dismissResult()
        assertEquals(WorkoutState.Idle, runner.state.value)
    }

    @Test
    fun stoppingSavesAnIncompleteSessionWithThePausesLeftOut() = runTest(UnconfinedTestDispatcher()) {
        val runner = newRunner()
        runner.start(savedRoutineId())
        clock.advance(20_000)
        runner.pause()
        clock.advance(300_000)
        runner.resume()
        clock.advance(10_000)

        runner.stop()

        assertEquals(WorkoutState.Idle, runner.state.value)
        val session = sessions.saved.single()
        assertEquals(30, session.durationSeconds)
        assertFalse(session.completed)
        assertNull(store.stored)
    }

    @Test
    fun anAccidentalStartIsNotAddedToTheHistory() = runTest(UnconfinedTestDispatcher()) {
        val runner = newRunner()
        runner.start(savedRoutineId())
        clock.advance(2_000)

        runner.stop()

        assertTrue(sessions.saved.isEmpty())
    }

    @Test
    fun aWorkoutThatEndedBeforeTheStopCallStillCountsAsCompleted() = runTest(UnconfinedTestDispatcher()) {
        val runner = newRunner()
        runner.start(savedRoutineId())
        clock.advance(90_000) // nobody ticked in between

        runner.stop()

        assertTrue(sessions.saved.single().completed)
        assertTrue(runner.state.value is WorkoutState.Finished)
    }

    @Test
    fun theLastStartedRoutineIsRemembered() = runTest(UnconfinedTestDispatcher()) {
        val runner = newRunner()
        val id = savedRoutineId()

        runner.start(id)

        assertEquals(id, settings.lastRoutineId.first())
    }

    // --- recovery ----------------------------------------------------------------------------------

    @Test
    fun afterTheProcessDiesTheRestoredWorkoutFollowsTheClockNotTheLastSavedMoment() = runTest(UnconfinedTestDispatcher()) {
        val before = newRunner()
        before.start(savedRoutineId())
        clock.advance(12_000) // 2s into the first work phase
        before.tick()
        assertEquals(1, (before.state.value as WorkoutState.Active).timer.phaseIndex)
        // The process dies here. The store still holds the snapshot taken at start.

        clock.advance(30_000) // 42s in: 2s into the second work phase after the rest
        val after = newRunner()

        assertTrue(after.restore())

        val state = after.state.value as WorkoutState.Active
        assertEquals("Sample", state.routine.name)
        assertEquals(3, state.timer.phaseIndex)
        assertEquals(PhaseKind.WORK, state.timer.phase.kind)
        assertEquals(2_000L, state.timer.phaseElapsedMs)
        assertEquals(75_000L - 42_000L, state.timer.totalRemainingMs)
    }

    @Test
    fun restoringDoesNotBeepForThePhaseAlreadyUnderway() = runTest(UnconfinedTestDispatcher()) {
        val before = newRunner()
        before.start(savedRoutineId())
        clock.advance(15_000)
        feedback.events.clear()

        newRunner().restore()

        assertTrue(feedback.events.isEmpty())
    }

    @Test
    fun aPausedWorkoutIsRestoredPausedWithTheSameTimeLeft() = runTest(UnconfinedTestDispatcher()) {
        val before = newRunner()
        before.start(savedRoutineId())
        clock.advance(15_000)
        before.pause()
        val left = (before.state.value as WorkoutState.Active).timer.phaseRemainingMs

        clock.advance(3_600_000) // an hour later
        val after = newRunner()
        after.restore()

        val state = after.state.value as WorkoutState.Active
        assertTrue(state.timer.isPaused)
        assertEquals(left, state.timer.phaseRemainingMs)
    }

    @Test
    fun theRestoredWorkoutKeepsWorkingAndCanFinish() = runTest(UnconfinedTestDispatcher()) {
        newRunner().start(savedRoutineId())
        clock.advance(20_000)
        val after = newRunner()
        after.restore()

        clock.advance(55_000)
        after.tick()

        assertTrue(after.state.value is WorkoutState.Finished)
        assertEquals(75, sessions.saved.single().durationSeconds)
        assertNull(store.stored)
    }

    @Test
    fun aWorkoutThatEndedWhileTheProcessWasDeadIsRecordedOnRestore() = runTest(UnconfinedTestDispatcher()) {
        val before = newRunner()
        before.start(savedRoutineId())
        clock.advance(10 * 60_000L)

        val after = newRunner()
        assertTrue(after.restore())

        val finished = after.state.value as WorkoutState.Finished
        assertEquals(75, finished.activeSeconds)
        assertTrue(sessions.saved.single().completed)
        assertNull(store.stored)
        assertFalse("finished" in feedback.events)
    }

    @Test
    fun aRebootInvalidatesTheStoredWorkout() = runTest(UnconfinedTestDispatcher()) {
        val before = newRunner()
        before.start(savedRoutineId())

        // elapsedRealtime starts again from zero after a reboot.
        val rebooted = FakeClock(elapsed = 500L, wall = clock.wall + 3_600_000)
        val after = newRunner(rebooted)

        assertFalse(after.restore())
        assertEquals(WorkoutState.Idle, after.state.value)
        assertNull(store.stored)
    }

    @Test
    fun restoringWithNothingStoredDoesNothing() = runTest(UnconfinedTestDispatcher()) {
        val runner = newRunner()

        assertFalse(runner.restore())
        assertEquals(WorkoutState.Idle, runner.state.value)
    }

    @Test
    fun restoringIsHarmlessWhenAlreadyRunning() = runTest(UnconfinedTestDispatcher()) {
        val runner = newRunner()
        runner.start(savedRoutineId())
        clock.advance(5_000)

        assertTrue(runner.restore())
        runner.tick()

        assertEquals(5_000L, (runner.state.value as WorkoutState.Active).timer.phaseElapsedMs)
    }
}
