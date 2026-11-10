package dev.bastian.intervalo.ui

import dev.bastian.intervalo.domain.RoutineError
import dev.bastian.intervalo.domain.Step
import dev.bastian.intervalo.domain.StepType
import dev.bastian.intervalo.testing.FakeRoutineRepository
import dev.bastian.intervalo.testing.MainDispatcherRule
import dev.bastian.intervalo.testing.routine
import dev.bastian.intervalo.ui.editor.EditorViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRoutineRepository()

    private fun newEditor() = EditorViewModel(0, repository)

    @Test
    fun newRoutineStartsWithTwoDefaultStepsAndNoVisibleErrors() {
        val state = newEditor().uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.isNew)
        assertEquals(listOf(StepType.WORK, StepType.REST), state.steps.map { it.type })
        assertNull(state.validation)
    }

    @Test
    fun savingWithBlankNameShowsErrorAndDoesNotPersist() = runTest {
        val editor = newEditor()

        editor.save()

        assertEquals(setOf(RoutineError.NAME_BLANK), editor.uiState.value.validation!!.errors)
        assertFalse(editor.uiState.value.saved)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun savingWithoutStepsIsRejected() = runTest {
        val editor = newEditor()
        editor.setName("Empty")
        editor.uiState.value.steps.forEach { editor.removeStep(it.key) }

        editor.save()

        assertTrue(RoutineError.NO_STEPS in editor.uiState.value.validation!!.errors)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun errorsClearAsTheUserFixesTheForm() {
        val editor = newEditor()
        editor.save()
        assertFalse(editor.uiState.value.validation!!.isValid)

        editor.setName("Fixed")

        assertTrue(editor.uiState.value.validation!!.isValid)
    }

    @Test
    fun createsRoutineWithSeveralStepsReordersAndPersists() = runTest {
        val editor = newEditor()
        editor.setName("  Circuit ")
        editor.setRounds(6)
        editor.setWarmup(90)
        editor.setCooldown(45)
        editor.uiState.value.steps.forEach { editor.removeStep(it.key) }
        editor.addStep("Burpees", StepType.WORK, 40)
        editor.addStep("", StepType.REST, 20)
        editor.addStep("Lunges", StepType.WORK, 30)
        val keys = editor.uiState.value.steps.map { it.key }

        editor.moveStep(keys[2], keys[0])
        editor.save()

        assertTrue(editor.uiState.value.saved)
        val stored = repository.saved.single()
        assertEquals("Circuit", stored.name)
        assertEquals(6, stored.rounds)
        assertEquals(90, stored.warmupSeconds)
        assertEquals(45, stored.cooldownSeconds)
        assertEquals(
            listOf(
                Step("Lunges", StepType.WORK, 30),
                Step("Burpees", StepType.WORK, 40),
                Step("", StepType.REST, 20),
            ),
            stored.steps,
        )
    }

    @Test
    fun moveStepByShiftsOneIndexAndIgnoresTheEdges() {
        val editor = newEditor()
        editor.addStep("Third", StepType.WORK, 10)
        val keys = editor.uiState.value.steps.map { it.key }

        editor.moveStepBy(keys[2], -1)
        assertEquals(listOf(keys[0], keys[2], keys[1]), editor.uiState.value.steps.map { it.key })

        editor.moveStepBy(keys[0], -1)
        editor.moveStepBy(keys[1], 1)
        assertEquals(listOf(keys[0], keys[2], keys[1]), editor.uiState.value.steps.map { it.key })
    }

    @Test
    fun updateStepChangesOnlyThatStep() {
        val editor = newEditor()
        val (first, second) = editor.uiState.value.steps

        editor.updateStep(first.key, "Sprint", StepType.WORK, 45)

        val steps = editor.uiState.value.steps
        assertEquals("Sprint", steps[0].name)
        assertEquals(45, steps[0].durationSeconds)
        assertEquals(second, steps[1])
    }

    @Test
    fun valuesAreClampedToTheirLimits() {
        val editor = newEditor()

        editor.setRounds(0)
        assertEquals(1, editor.uiState.value.rounds)
        editor.setRounds(1_000)
        assertEquals(99, editor.uiState.value.rounds)
        editor.setWarmup(-5)
        assertEquals(0, editor.uiState.value.warmupSeconds)
    }

    @Test
    fun editingAnExistingRoutineLoadsAndSavesInPlace() = runTest {
        val id = repository.save(routine(name = "Old", rounds = 2, steps = listOf(Step("A", StepType.WORK, 10))))
        val editor = EditorViewModel(id, repository)

        val loaded = editor.uiState.value
        assertFalse(loaded.isLoading)
        assertFalse(loaded.isNew)
        assertEquals("Old", loaded.name)

        editor.setName("New")
        editor.save()

        assertEquals(1, repository.saved.size)
        assertEquals("New", repository.saved.single().name)
        assertEquals(id, repository.saved.single().id)
    }

    @Test
    fun openingAMissingRoutineReportsNotFound() {
        val editor = EditorViewModel(404, repository)

        assertTrue(editor.uiState.value.notFound)
        assertNotNull(editor.uiState.value)
    }
}
