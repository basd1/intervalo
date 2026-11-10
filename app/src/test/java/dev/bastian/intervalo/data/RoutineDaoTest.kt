package dev.bastian.intervalo.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bastian.intervalo.data.db.IntervaloDatabase
import dev.bastian.intervalo.data.repo.RoomRoutineRepository
import dev.bastian.intervalo.data.repo.RoomSessionRepository
import dev.bastian.intervalo.domain.Session
import dev.bastian.intervalo.domain.Step
import dev.bastian.intervalo.domain.StepType
import dev.bastian.intervalo.testing.inMemoryDatabase
import dev.bastian.intervalo.testing.routine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoutineDaoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: IntervaloDatabase
    private lateinit var repository: RoomRoutineRepository

    @Before
    fun setUp() {
        database = inMemoryDatabase(context)
        repository = RoomRoutineRepository(database.routineDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savedRoutineKeepsStepsInOrder() = runTest {
        val steps = listOf(
            Step("Squats", StepType.WORK, 40),
            Step("", StepType.REST, 20),
            Step("Plank", StepType.WORK, 30),
        )
        val id = repository.save(routine(name = "Legs", warmup = 60, cooldown = 45, rounds = 4, steps = steps))

        val loaded = repository.get(id)!!

        assertEquals("Legs", loaded.name)
        assertEquals(60, loaded.warmupSeconds)
        assertEquals(45, loaded.cooldownSeconds)
        assertEquals(4, loaded.rounds)
        assertEquals(steps, loaded.steps)
    }

    @Test
    fun reorderedStepsPersistInTheNewOrder() = runTest {
        val a = Step("A", StepType.WORK, 10)
        val b = Step("B", StepType.REST, 20)
        val c = Step("C", StepType.WORK, 30)
        val id = repository.save(routine(steps = listOf(a, b, c)))

        repository.save(repository.get(id)!!.copy(steps = listOf(c, a, b)))

        assertEquals(listOf(c, a, b), repository.get(id)!!.steps)
    }

    @Test
    fun updatingReplacesStepsInsteadOfAppendingThem() = runTest {
        val id = repository.save(routine(steps = listOf(Step("A", StepType.WORK, 10), Step("B", StepType.REST, 5))))

        repository.save(repository.get(id)!!.copy(steps = listOf(Step("Only", StepType.WORK, 15))))

        assertEquals(listOf(Step("Only", StepType.WORK, 15)), repository.get(id)!!.steps)
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun deletingARoutineDeletesItsSteps() = runTest {
        val id = repository.save(routine())

        repository.delete(id)

        assertNull(repository.get(id))
        assertEquals(0, database.query("SELECT COUNT(*) FROM steps", null).use { it.moveToFirst(); it.getInt(0) })
    }

    @Test
    fun duplicateCreatesIndependentCopy() = runTest {
        val id = repository.save(routine(name = "Original", rounds = 3))

        val copyId = repository.duplicate(id, "Original (copy)")!!

        assertNotEquals(id, copyId)
        val copy = repository.get(copyId)!!
        assertEquals("Original (copy)", copy.name)
        assertEquals(3, copy.rounds)
        assertEquals(repository.get(id)!!.steps, copy.steps)
        assertEquals(2, repository.count())
    }

    @Test
    fun duplicateOfMissingRoutineIsNull() = runTest {
        assertNull(repository.duplicate(99, "x"))
    }

    @Test
    fun sessionsOutliveTheirRoutineAndComeNewestFirst() = runTest {
        val sessions = RoomSessionRepository(database.sessionDao())
        val id = repository.save(routine(name = "Gone"))
        sessions.add(Session(routineId = id, routineName = "Gone", startedAt = 100, durationSeconds = 60, completed = true))
        sessions.add(Session(routineId = id, routineName = "Gone", startedAt = 200, durationSeconds = 30, completed = false))

        repository.delete(id)

        val all = sessions.observeAll().first()
        assertEquals(listOf(200L, 100L), all.map { it.startedAt })
        assertEquals(listOf(false, true), all.map { it.completed })
    }
}
