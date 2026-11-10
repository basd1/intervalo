package dev.bastian.intervalo.domain

import dev.bastian.intervalo.testing.routine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    @Test
    fun aWellFormedRoutineIsValid() {
        assertTrue(validateRoutine(routine(name = "Ok")).isValid)
    }

    @Test
    fun reportsEveryProblemAtOnce() {
        val bad = routine(name = " ", rounds = 0, warmup = -1, cooldown = 4000, steps = emptyList())

        assertEquals(
            setOf(
                RoutineError.NAME_BLANK,
                RoutineError.NO_STEPS,
                RoutineError.ROUNDS_OUT_OF_RANGE,
                RoutineError.WARMUP_OUT_OF_RANGE,
                RoutineError.COOLDOWN_OUT_OF_RANGE,
            ),
            validateRoutine(bad).errors,
        )
    }

    @Test
    fun flagsStepsWithBadDurations() {
        val r = routine(steps = listOf(Step("", StepType.WORK, 10), Step("", StepType.REST, 0), Step("", StepType.WORK, 5000)))

        val validation = validateRoutine(r)

        assertFalse(validation.isValid)
        assertEquals(setOf(1, 2), validation.invalidStepIndices)
    }

    @Test
    fun rejectsTooLongNames() {
        assertTrue(RoutineError.NAME_TOO_LONG in validateRoutine(routine(name = "x".repeat(41))).errors)
    }

    @Test
    fun totalDurationCountsEveryRound() {
        val r = routine(warmup = 60, cooldown = 30, rounds = 8, steps = listOf(Step("", StepType.WORK, 20), Step("", StepType.REST, 10)))

        assertEquals(60 + 8 * 30 + 30, r.totalSeconds)
    }

    @Test
    fun formatsDurations() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:09", formatDuration(9))
        assertEquals("4:05", formatDuration(245))
        assertEquals("1:01:01", formatDuration(3661))
        assertEquals("0:00", formatDuration(-3))
    }
}
