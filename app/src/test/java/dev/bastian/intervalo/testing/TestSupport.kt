package dev.bastian.intervalo.testing

import android.content.Context
import androidx.room.Room
import dev.bastian.intervalo.data.db.IntervaloDatabase
import dev.bastian.intervalo.domain.Clock
import dev.bastian.intervalo.domain.Routine
import dev.bastian.intervalo.domain.RoutineRepository
import dev.bastian.intervalo.domain.Session
import dev.bastian.intervalo.domain.SessionRepository
import dev.bastian.intervalo.domain.Step
import dev.bastian.intervalo.domain.StepType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** A clock the test moves by hand. `elapsed` and `wall` advance together unless set separately. */
class FakeClock(var elapsed: Long = 10_000L, var wall: Long = 1_700_000_000_000L) : Clock {
    override fun now(): Long = wall
    override fun elapsedRealtime(): Long = elapsed

    fun advance(millis: Long) {
        elapsed += millis
        wall += millis
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun inMemoryDatabase(context: Context): IntervaloDatabase =
    Room.inMemoryDatabaseBuilder(context, IntervaloDatabase::class.java)
        .allowMainThreadQueries()
        .build()

fun routine(
    id: Long = 0,
    name: String = "Test",
    warmup: Int = 0,
    cooldown: Int = 0,
    rounds: Int = 1,
    steps: List<Step> = listOf(Step("", StepType.WORK, 30), Step("", StepType.REST, 10)),
) = Routine(
    id = id,
    name = name,
    color = 0xFF112233.toInt(),
    warmupSeconds = warmup,
    cooldownSeconds = cooldown,
    rounds = rounds,
    steps = steps,
)

class FakeRoutineRepository : RoutineRepository {
    private val routines = MutableStateFlow<List<Routine>>(emptyList())
    private var nextId = 1L

    val saved: List<Routine> get() = routines.value

    override fun observeAll(): Flow<List<Routine>> = routines
    override fun observe(id: Long): Flow<Routine?> = routines.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun get(id: Long): Routine? = routines.value.firstOrNull { it.id == id }

    override suspend fun save(routine: Routine): Long {
        val id = if (routine.id == 0L) nextId++ else routine.id
        val stored = routine.copy(id = id)
        routines.value = if (routines.value.any { it.id == id }) {
            routines.value.map { if (it.id == id) stored else it }
        } else {
            routines.value + stored
        }
        return id
    }

    override suspend fun delete(id: Long) {
        routines.value = routines.value.filterNot { it.id == id }
    }

    override suspend fun duplicate(id: Long, copyName: String): Long? =
        get(id)?.let { save(it.copy(id = 0, name = copyName)) }

    override suspend fun count(): Int = routines.value.size
}

class FakeSessionRepository : SessionRepository {
    private val sessions = MutableStateFlow<List<Session>>(emptyList())
    val saved: List<Session> get() = sessions.value

    override fun observeAll(): Flow<List<Session>> = sessions.map { it.sortedByDescending { s -> s.startedAt } }

    override suspend fun add(session: Session): Long {
        val id = sessions.value.size + 1L
        sessions.value = sessions.value + session.copy(id = id)
        return id
    }

    override suspend fun clear() {
        sessions.value = emptyList()
    }
}
