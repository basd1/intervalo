package dev.bastian.intervalo.domain

import dev.bastian.intervalo.domain.timer.PhaseKind
import dev.bastian.intervalo.domain.timer.TimerClockState
import dev.bastian.intervalo.domain.timer.TimerEngine
import dev.bastian.intervalo.domain.timer.TimerStatus
import dev.bastian.intervalo.domain.timer.buildPlan
import dev.bastian.intervalo.testing.FakeClock
import dev.bastian.intervalo.testing.routine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {

    private val clock = FakeClock()

    // Warm-up 10s, 2 rounds of (work 20s, rest 10s), cool-down 5s: 10 + 60 + 5 = 75s, 6 phases.
    private val sample = routine(
        warmup = 10,
        cooldown = 5,
        rounds = 2,
        steps = listOf(Step("", StepType.WORK, 20), Step("Breathe", StepType.REST, 10)),
    )

    private fun engine(from: TimerClockState? = null) = TimerEngine(buildPlan(sample), clock, from)

    // --- plan -----------------------------------------------------------------------------

    @Test
    fun planHasWarmupStepsTimesRoundsAndCooldown() {
        val plan = buildPlan(sample)

        assertEquals(
            listOf(PhaseKind.WARMUP, PhaseKind.WORK, PhaseKind.REST, PhaseKind.WORK, PhaseKind.REST, PhaseKind.COOLDOWN),
            plan.map { it.kind },
        )
        assertEquals(listOf(0, 1, 1, 2, 2, 0), plan.map { it.round })
        assertEquals(listOf(10_000L, 20_000L, 10_000L, 20_000L, 10_000L, 5_000L), plan.map { it.durationMs })
        assertEquals("Breathe", plan[2].name)
    }

    @Test
    fun zeroLengthWarmupAndCooldownProduceNoPhase() {
        val plan = buildPlan(routine(warmup = 0, cooldown = 0, rounds = 1, steps = listOf(Step("", StepType.WORK, 5))))

        assertEquals(listOf(PhaseKind.WORK), plan.map { it.kind })
    }

    @Test
    fun planDurationMatchesRoutineTotal() {
        assertEquals(sample.totalSeconds * 1000L, buildPlan(sample).sumOf { it.durationMs })
    }

    // --- advancing ---------------------------------------------------------------------------

    @Test
    fun startsInTheFirstPhaseWithFullTimeRemaining() {
        val state = engine().state.value

        assertEquals(TimerStatus.RUNNING, state.status)
        assertEquals(0, state.phaseIndex)
        assertEquals(10_000L, state.phaseRemainingMs)
        assertEquals(75_000L, state.totalRemainingMs)
        assertEquals(PhaseKind.WORK, state.nextPhase?.kind)
    }

    @Test
    fun movesThroughPhasesAsTimePasses() {
        val engine = engine()

        clock.advance(4_000)
        engine.tick()
        assertEquals(0, engine.state.value.phaseIndex)
        assertEquals(6_000L, engine.state.value.phaseRemainingMs)

        clock.advance(6_000) // exactly the boundary: the next phase starts
        engine.tick()
        assertEquals(1, engine.state.value.phaseIndex)
        assertEquals(20_000L, engine.state.value.phaseRemainingMs)

        clock.advance(25_000)
        engine.tick()
        assertEquals(2, engine.state.value.phaseIndex)
        assertEquals(PhaseKind.REST, engine.state.value.phase.kind)
        assertEquals(5_000L, engine.state.value.phaseRemainingMs)
        assertEquals(75_000L - 35_000L, engine.state.value.totalRemainingMs)
    }

    @Test
    fun manySmallTicksAndOneBigJumpGiveTheSameState() {
        val ticking = engine()
        repeat(487) {
            clock.advance(100)
            ticking.tick()
        }
        val other = FakeClock(elapsed = 10_000L)
        val jumping = TimerEngine(buildPlan(sample), other)
        other.advance(48_700)
        jumping.tick()

        assertEquals(ticking.state.value, jumping.state.value)
    }

    // --- pause and resume --------------------------------------------------------------------

    @Test
    fun pausingFreezesTheStateAndResumingContinuesWithoutDrift() {
        val engine = engine()
        clock.advance(12_000)
        engine.tick()
        val before = engine.state.value

        engine.pause()
        clock.advance(10 * 60_000) // ten minutes paused
        engine.tick()
        assertEquals(TimerStatus.PAUSED, engine.state.value.status)
        assertEquals(before.phaseIndex, engine.state.value.phaseIndex)
        assertEquals(before.phaseRemainingMs, engine.state.value.phaseRemainingMs)

        engine.resume()
        assertEquals(TimerStatus.RUNNING, engine.state.value.status)
        assertEquals(before.phaseRemainingMs, engine.state.value.phaseRemainingMs)
        clock.advance(3_000)
        engine.tick()
        assertEquals(before.phaseRemainingMs - 3_000, engine.state.value.phaseRemainingMs)
    }

    @Test
    fun repeatedPauseResumeCyclesAccumulateNoError() {
        val engine = engine()
        repeat(20) {
            clock.advance(500)
            engine.pause()
            clock.advance(1_234)
            engine.resume()
        }
        clock.advance(500)
        engine.tick()

        assertEquals(75_000L - 21 * 500L, engine.state.value.totalRemainingMs)
        assertEquals(21 * 500L, engine.activeDurationMs())
    }

    @Test
    fun pauseAndResumeAreIdempotent() {
        val engine = engine()
        engine.resume() // nothing to resume
        clock.advance(1_000)
        engine.pause()
        clock.advance(1_000)
        engine.pause() // already paused: must not move the pause instant
        engine.resume()
        engine.tick()

        assertEquals(10_000L - 1_000L, engine.state.value.phaseRemainingMs)
    }

    // --- skipping ----------------------------------------------------------------------------

    @Test
    fun skipToNextStartsTheNextPhaseAndKeepsRunning() {
        val engine = engine()
        clock.advance(3_000)

        engine.skipToNext()

        assertEquals(1, engine.state.value.phaseIndex)
        assertEquals(20_000L, engine.state.value.phaseRemainingMs)
        clock.advance(5_000)
        engine.tick()
        assertEquals(15_000L, engine.state.value.phaseRemainingMs)
    }

    @Test
    fun skippingWhilePausedStaysPausedOnTheNewPhase() {
        val engine = engine()
        clock.advance(3_000)
        engine.pause()

        engine.skipToNext()
        clock.advance(60_000)
        engine.tick()

        assertEquals(TimerStatus.PAUSED, engine.state.value.status)
        assertEquals(1, engine.state.value.phaseIndex)
        assertEquals(20_000L, engine.state.value.phaseRemainingMs)
    }

    @Test
    fun previousRestartsThePhaseAfterTwoSecondsOtherwiseGoesBack() {
        val engine = engine()
        clock.advance(10_000 + 20_000 + 5_000) // 5s into the rest phase (index 2)
        engine.tick()
        assertEquals(2, engine.state.value.phaseIndex)

        engine.skipToPrevious() // more than 2s in: restart this phase
        assertEquals(2, engine.state.value.phaseIndex)
        assertEquals(10_000L, engine.state.value.phaseRemainingMs)

        clock.advance(1_000)
        engine.skipToPrevious() // within 2s: go back to the work phase
        assertEquals(1, engine.state.value.phaseIndex)
        assertEquals(20_000L, engine.state.value.phaseRemainingMs)
    }

    @Test
    fun previousInTheFirstPhaseRestartsIt() {
        val engine = engine()
        clock.advance(1_000)

        engine.skipToPrevious()

        assertEquals(0, engine.state.value.phaseIndex)
        assertEquals(10_000L, engine.state.value.phaseRemainingMs)
    }

    // --- finishing ---------------------------------------------------------------------------

    @Test
    fun finishesWhenTheWholePlanHasElapsed() {
        val engine = engine()

        clock.advance(75_000)
        engine.tick()

        val state = engine.state.value
        assertTrue(state.isFinished)
        assertEquals(PhaseKind.COOLDOWN, state.phase.kind)
        assertEquals(0L, state.phaseRemainingMs)
        assertEquals(0L, state.totalRemainingMs)
        assertEquals(1f, state.phaseProgress, 0f)
        assertEquals(75_000L, engine.activeDurationMs())
    }

    @Test
    fun skippingTheLastPhaseFinishes() {
        val engine = engine()
        clock.advance(70_000) // 5s into... exactly at the cool-down start
        engine.tick()
        assertEquals(5, engine.state.value.phaseIndex)

        engine.skipToNext()

        assertTrue(engine.state.value.isFinished)
        assertEquals(70_000L, engine.activeDurationMs())
    }

    @Test
    fun stopFreezesTheTimeAndIgnoresLaterCommands() {
        val engine = engine()
        clock.advance(15_000)
        engine.stop()
        val stopped = engine.state.value

        clock.advance(60_000)
        engine.resume()
        engine.skipToNext()
        engine.tick()

        assertEquals(TimerStatus.STOPPED, engine.state.value.status)
        assertEquals(stopped.phaseRemainingMs, engine.state.value.phaseRemainingMs)
        assertEquals(15_000L, engine.activeDurationMs())
    }

    @Test
    fun stoppingAFinishedWorkoutKeepsItFinished() {
        val engine = engine()
        clock.advance(80_000)

        engine.stop()

        assertTrue(engine.state.value.isFinished)
    }

    // --- the process was asleep ----------------------------------------------------------------

    @Test
    fun aHugeJumpOverAnEntireWorkoutEndsFinishedNotOutOfRange() {
        val engine = engine()

        clock.advance(3 * 3_600_000L) // three hours with the process frozen
        engine.tick()

        assertTrue(engine.state.value.isFinished)
        assertEquals(5, engine.state.value.phaseIndex)
        assertEquals(0L, engine.state.value.totalRemainingMs)
        assertEquals(75_000L, engine.activeDurationMs())
    }

    @Test
    fun aJumpInTheMiddleLandsOnTheRightPhaseAndRemainder() {
        val engine = engine()

        clock.advance(10_000 + 20_000 + 10_000 + 7_500) // 7.5s into round 2's work
        engine.tick()

        val state = engine.state.value
        assertEquals(3, state.phaseIndex)
        assertEquals(PhaseKind.WORK, state.phase.kind)
        assertEquals(2, state.phase.round)
        assertEquals(12_500L, state.phaseRemainingMs)
        assertEquals(75_000L - 47_500L, state.totalRemainingMs)
    }

    @Test
    fun aJumpWhilePausedChangesNothing() {
        val engine = engine()
        clock.advance(25_000)
        engine.pause()

        clock.advance(5 * 3_600_000L)
        engine.tick()

        assertFalse(engine.state.value.isFinished)
        assertEquals(1, engine.state.value.phaseIndex)
        assertEquals(15_000L, engine.state.value.phaseElapsedMs)
    }

    // --- restoring ---------------------------------------------------------------------------

    @Test
    fun anEngineRebuiltFromASnapshotContinuesAsIfNothingHappened() {
        val original = engine()
        clock.advance(33_000)
        original.skipToNext()
        clock.advance(2_000)
        original.pause()
        clock.advance(8_000)
        original.resume()
        val snapshot = original.snapshot

        clock.advance(90_000 / 9) // 10s while "the process was dead"
        original.tick()
        val rebuilt = engine(from = snapshot)

        assertEquals(original.state.value, rebuilt.state.value)
    }
}
