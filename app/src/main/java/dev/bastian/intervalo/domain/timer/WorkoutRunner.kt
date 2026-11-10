package dev.bastian.intervalo.domain.timer

import dev.bastian.intervalo.domain.ActiveWorkout
import dev.bastian.intervalo.domain.ActiveWorkoutStore
import dev.bastian.intervalo.domain.Clock
import dev.bastian.intervalo.domain.PhaseFeedback
import dev.bastian.intervalo.domain.Routine
import dev.bastian.intervalo.domain.RoutineRepository
import dev.bastian.intervalo.domain.Session
import dev.bastian.intervalo.domain.SessionRepository
import dev.bastian.intervalo.domain.Settings
import dev.bastian.intervalo.domain.SettingsRepository
import dev.bastian.intervalo.domain.WorkoutState
import dev.bastian.intervalo.domain.validateRoutine
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the one workout that can be active: its [TimerEngine], the cues, the persisted copy and the
 * history entry. It has no Android types, so everything the service does can be tested on the JVM.
 *
 * Commands are safe to call from any thread. [state] always reflects the engine as of the last
 * [tick] (the ticker coroutine calls it every [tickIntervalMs], and every command calls it too).
 */
class WorkoutRunner(
    private val routines: RoutineRepository,
    private val sessions: SessionRepository,
    private val store: ActiveWorkoutStore,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val feedback: PhaseFeedback,
    private val scope: CoroutineScope,
    private val tickIntervalMs: Long = 200,
) {
    private val lock = Any()
    private val storeMutex = Mutex()
    private val _state = MutableStateFlow<WorkoutState>(WorkoutState.Idle)
    val state: StateFlow<WorkoutState> = _state.asStateFlow()

    private var engine: TimerEngine? = null
    private var routine: Routine? = null
    private var startedAtWall = 0L
    private var ticker: Job? = null
    private var lastPhaseIndex = -1
    private var lastCountdownKey = -1

    @Volatile
    private var currentSettings = Settings()

    init {
        scope.launch { settings.settings.collect { currentSettings = it } }
    }

    /** Starts a workout. Returns false if the routine is gone or invalid; true if one is already running. */
    suspend fun start(routineId: Long): Boolean {
        if (synchronized(lock) { engine != null }) return true
        val target = routines.get(routineId) ?: return false
        if (!validateRoutine(target).isValid) return false
        val started = synchronized(lock) {
            if (engine != null) return@synchronized false
            engine = TimerEngine(buildPlan(target), clock)
            routine = target
            startedAtWall = clock.now()
            lastPhaseIndex = -1
            lastCountdownKey = -1
            tickLocked()
            startTickerLocked()
            true
        }
        if (started) {
            settings.setLastRoutine(routineId)
            syncStore()
        }
        return true
    }

    /**
     * Rebuilds the workout that was running when the process died, from the stored copy and the
     * monotonic clock. Returns false when there is nothing valid to restore.
     */
    suspend fun restore(): Boolean {
        if (synchronized(lock) { engine != null }) return true
        val saved = store.load() ?: return false
        // elapsedRealtime restarts from zero on reboot, so a start in the "future" means the device rebooted.
        if (saved.timer.startElapsed > clock.elapsedRealtime()) {
            store.clear()
            return false
        }
        synchronized(lock) {
            if (engine != null) return true
            engine = TimerEngine(buildPlan(saved.routine), clock, saved.timer)
            routine = saved.routine
            startedAtWall = saved.startedAtWall
            // The phase is already underway: do not beep for it.
            lastPhaseIndex = engine!!.state.value.phaseIndex
            lastCountdownKey = -1
            tickLocked(silent = true)
            if (engine != null) startTickerLocked()
        }
        syncStore()
        return true
    }

    fun togglePause() = synchronized(lock) {
        val current = engine ?: return@synchronized
        current.tick()
        if (current.state.value.isPaused) current.resume() else current.pause()
        tickLocked()
        syncStore()
    }

    fun pause() = synchronized(lock) {
        engine?.pause()
        tickLocked()
        syncStore()
    }

    fun resume() = synchronized(lock) {
        engine?.resume()
        tickLocked()
        syncStore()
    }

    fun skipNext() = synchronized(lock) {
        engine?.skipToNext()
        tickLocked()
        syncStore()
    }

    fun skipPrevious() = synchronized(lock) {
        engine?.skipToPrevious()
        tickLocked()
        syncStore()
    }

    /** Ends the workout early and records it as not completed. */
    fun stop() = synchronized(lock) {
        val current = engine ?: return@synchronized
        val target = routine ?: return@synchronized
        current.stop()
        val seconds = toSeconds(current.activeDurationMs())
        // A workout that ran to its end between the last tick and this call still counts as completed.
        val completed = current.state.value.isFinished
        endLocked()
        recordSession(target, seconds, completed)
        _state.value = if (completed) WorkoutState.Finished(target, seconds) else WorkoutState.Idle
        syncStore()
    }

    /** Called by the ticker and by every command; also the entry point for tests. */
    fun tick() = synchronized(lock) { tickLocked() }

    /** Leaves the "finished" summary and returns to idle. */
    fun dismissResult() {
        _state.value.let { if (it is WorkoutState.Finished) _state.value = WorkoutState.Idle }
    }

    private fun tickLocked(silent: Boolean = false) {
        val current = engine ?: return
        val target = routine ?: return
        current.tick()
        val timer = current.state.value
        if (timer.isFinished) {
            finishLocked(current, target, silent)
            return
        }
        if (!silent) emitCues(timer)
        lastPhaseIndex = timer.phaseIndex
        _state.value = WorkoutState.Active(target, timer, startedAtWall)
    }

    private fun emitCues(timer: TimerState) {
        val now = currentSettings
        if (timer.phaseIndex != lastPhaseIndex) {
            feedback.phaseStarted(timer.phase.kind, now)
            lastCountdownKey = -1
        }
        if (timer.status == TimerStatus.RUNNING && now.countdown) {
            val secondsLeft = ceil(timer.phaseRemainingMs / 1000.0).toInt()
            if (secondsLeft in 1..COUNTDOWN_SECONDS) {
                val key = timer.phaseIndex * 10 + secondsLeft
                if (key != lastCountdownKey) {
                    lastCountdownKey = key
                    feedback.countdownTick(now)
                }
            }
        }
    }

    private fun finishLocked(current: TimerEngine, target: Routine, silent: Boolean) {
        val seconds = toSeconds(current.activeDurationMs())
        endLocked()
        if (!silent) feedback.finished(currentSettings)
        recordSession(target, seconds, completed = true)
        _state.value = WorkoutState.Finished(target, seconds)
        syncStore()
    }

    private fun endLocked() {
        ticker?.cancel()
        ticker = null
        engine = null
        routine = null
    }

    private fun startTickerLocked() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(tickIntervalMs)
                tick()
            }
        }
    }

    private fun recordSession(target: Routine, seconds: Int, completed: Boolean) {
        if (seconds < MIN_RECORDED_SECONDS) return
        val session = Session(
            routineId = target.id,
            routineName = target.name,
            startedAt = startedAtWall,
            durationSeconds = seconds,
            completed = completed,
        )
        scope.launch { sessions.add(session) }
    }

    /**
     * Writes the current state of the workout to the store (or clears it if none is running).
     * The snapshot is read when the write runs, so the last write always carries the latest state.
     */
    private fun syncStore() {
        scope.launch {
            storeMutex.withLock {
                val snapshot = synchronized(lock) {
                    val current = engine
                    val target = routine
                    if (current != null && target != null) ActiveWorkout(target, startedAtWall, current.snapshot) else null
                }
                if (snapshot == null) store.clear() else store.save(snapshot)
            }
        }
    }

    private fun toSeconds(millis: Long): Int = ((millis + 500) / 1000).toInt()

    companion object {
        const val COUNTDOWN_SECONDS = 3

        /** Sessions shorter than this are accidental starts and are not added to the history. */
        const val MIN_RECORDED_SECONDS = 5
    }
}
