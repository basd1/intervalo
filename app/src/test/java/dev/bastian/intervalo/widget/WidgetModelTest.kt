package dev.bastian.intervalo.widget

import dev.bastian.intervalo.domain.Step
import dev.bastian.intervalo.domain.StepType
import dev.bastian.intervalo.domain.WorkoutState
import dev.bastian.intervalo.domain.timer.PhaseKind
import dev.bastian.intervalo.domain.timer.TimerEngine
import dev.bastian.intervalo.domain.timer.buildPlan
import dev.bastian.intervalo.testing.FakeClock
import dev.bastian.intervalo.testing.routine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetModelTest {

    private val clock = FakeClock()
    private val sample = routine(
        id = 3,
        name = "Sample",
        warmup = 10,
        rounds = 1,
        steps = listOf(Step("", StepType.WORK, 20)),
    )

    private fun active(configure: (TimerEngine) -> Unit = {}): WorkoutState.Active {
        val engine = TimerEngine(buildPlan(sample), clock)
        configure(engine)
        engine.tick()
        return WorkoutState.Active(sample, engine.state.value, clock.now())
    }

    @Test
    fun withoutAWorkoutItOffersTheLastRoutine() {
        val model = widgetModel(WorkoutState.Idle, sample, clock.now())

        assertEquals(WidgetModel.Idle(3, "Sample"), model)
    }

    @Test
    fun beforeAnyRoutineWasStartedItHasNothingToOffer() {
        assertEquals(WidgetModel.Idle(null, null), widgetModel(WorkoutState.Idle, null, clock.now()))
    }

    @Test
    fun aFinishedWorkoutLooksLikeIdle() {
        val model = widgetModel(WorkoutState.Finished(sample, 30), sample, clock.now())

        assertEquals(WidgetModel.Idle(3, "Sample"), model)
    }

    @Test
    fun aRunningWorkoutShowsPhaseAndExpectedEnd() {
        val state = active { clock.advance(4_000) }

        val model = widgetModel(state, sample, clock.now()) as WidgetModel.Active

        assertEquals("Sample", model.routineName)
        assertEquals(PhaseKind.WARMUP, model.phase.kind)
        assertEquals(false, model.paused)
        assertEquals(clock.now() + 26_000, model.endsAtWall)
    }

    @Test
    fun aPausedWorkoutHasNoExpectedEnd() {
        val state = active {
            clock.advance(4_000)
            it.pause()
        }

        val model = widgetModel(state, sample, clock.now()) as WidgetModel.Active

        assertEquals(true, model.paused)
        assertNull(model.endsAtWall)
    }
}
