package dev.bastian.intervalo.widget

import app.cash.turbine.test
import dev.bastian.intervalo.domain.Step
import dev.bastian.intervalo.domain.StepType
import dev.bastian.intervalo.domain.timer.PhaseKind
import dev.bastian.intervalo.domain.timer.WorkoutRunner
import dev.bastian.intervalo.testing.FakeActiveWorkoutStore
import dev.bastian.intervalo.testing.FakeClock
import dev.bastian.intervalo.testing.FakePhaseFeedback
import dev.bastian.intervalo.testing.FakeRoutineRepository
import dev.bastian.intervalo.testing.FakeSessionRepository
import dev.bastian.intervalo.testing.FakeSettingsRepository
import dev.bastian.intervalo.testing.routine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetModelsTest {

    private val clock = FakeClock()
    private val routines = FakeRoutineRepository()
    private val settings = FakeSettingsRepository()

    @Test
    fun emitsOnlyWhenWhatTheWidgetShowsChanges() = runTest(UnconfinedTestDispatcher()) {
        val id = routines.save(
            routine(name = "Sample", warmup = 0, rounds = 1, steps = listOf(Step("", StepType.WORK, 20), Step("", StepType.REST, 20))),
        )
        val runner = WorkoutRunner(
            routines, FakeSessionRepository(), FakeActiveWorkoutStore(), settings, clock, FakePhaseFeedback(), backgroundScope,
        )
        val models = WidgetModels(runner, routines, settings, clock)

        models.observe().test {
            assertEquals(WidgetModel.Idle(null, null), awaitItem())

            runner.start(id)
            val first = awaitItem() as WidgetModel.Active
            assertEquals(PhaseKind.WORK, first.phase.kind)

            repeat(10) { // ticks inside the phase must not produce items
                clock.advance(1_000)
                runner.tick()
            }
            expectNoEvents()

            clock.advance(10_000) // 20 s in: the rest phase begins
            runner.tick()
            assertEquals(PhaseKind.REST, (awaitItem() as WidgetModel.Active).phase.kind)

            runner.togglePause()
            assertTrue((awaitItem() as WidgetModel.Active).paused)

            runner.stop()
            assertEquals(WidgetModel.Idle(id, "Sample"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
