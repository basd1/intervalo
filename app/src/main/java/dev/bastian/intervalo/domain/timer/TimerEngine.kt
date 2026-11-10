package dev.bastian.intervalo.domain.timer

import dev.bastian.intervalo.domain.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything needed to recompute where a workout is, as plain numbers.
 * All times are `elapsedRealtime` values (monotonic, keep counting while the device sleeps).
 *
 * `position = (now - startElapsed) - pausedTotalMs + skipOffsetMs`, where `now` is frozen at
 * `pausedAtElapsed` while paused. Skipping phases only changes `skipOffsetMs`.
 */
data class TimerClockState(
    val startElapsed: Long,
    val pausedTotalMs: Long = 0,
    val pausedAtElapsed: Long? = null,
    val skipOffsetMs: Long = 0,
)

enum class TimerStatus { RUNNING, PAUSED, FINISHED, STOPPED }

data class TimerState(
    val status: TimerStatus,
    val phaseIndex: Int,
    val phaseCount: Int,
    val phase: Phase,
    val nextPhase: Phase?,
    val phaseElapsedMs: Long,
    val phaseRemainingMs: Long,
    val totalElapsedMs: Long,
    val totalRemainingMs: Long,
    val totalDurationMs: Long,
) {
    val isPaused: Boolean get() = status == TimerStatus.PAUSED
    val isFinished: Boolean get() = status == TimerStatus.FINISHED

    /** Share of the current phase already done, 0..1. */
    val phaseProgress: Float
        get() = if (phase.durationMs == 0L) 1f else (phaseElapsedMs.toFloat() / phase.durationMs).coerceIn(0f, 1f)
}

/**
 * Pure workout timer. It never counts ticks: [state] is always recomputed from the plan and the
 * timestamps in [TimerClockState], so it stays exact even if the process is frozen for minutes.
 * Whoever owns the engine calls [tick] to refresh [state]; every command refreshes it too.
 */
class TimerEngine(
    private val plan: List<Phase>,
    private val clock: Clock,
    restoredFrom: TimerClockState? = null,
) {
    init {
        require(plan.isNotEmpty()) { "A workout needs at least one phase" }
    }

    private val totalMs = plan.sumOf { it.durationMs }

    // Start offsets of each phase within the whole workout.
    private val phaseStarts: List<Long> = plan.runningFold(0L) { acc, phase -> acc + phase.durationMs }.dropLast(1)

    private var clockState: TimerClockState = restoredFrom ?: TimerClockState(startElapsed = clock.elapsedRealtime())
    private var stopped = false

    private val _state = MutableStateFlow(compute())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    /** The numbers to persist so the workout can be rebuilt after the process dies. */
    val snapshot: TimerClockState get() = clockState

    val phases: List<Phase> get() = plan

    /** Recomputes [state] against the current clock. */
    fun tick() {
        _state.value = compute()
    }

    fun pause() {
        if (compute().status != TimerStatus.RUNNING) return
        clockState = clockState.copy(pausedAtElapsed = clock.elapsedRealtime())
        tick()
    }

    /** Ends the workout early. The time freezes and every further command is ignored. */
    fun stop() {
        if (stopped) return
        if (compute().isFinished) {
            tick()
            return
        }
        pause()
        stopped = true
        tick()
    }

    fun resume() {
        if (stopped) return
        val pausedAt = clockState.pausedAtElapsed ?: return
        clockState = clockState.copy(
            pausedTotalMs = clockState.pausedTotalMs + (clock.elapsedRealtime() - pausedAt),
            pausedAtElapsed = null,
        )
        tick()
    }

    /** Jumps to the start of the next phase; from the last phase it finishes the workout. */
    fun skipToNext() {
        val current = compute()
        if (current.isFinished || stopped) return
        val boundary = phaseStarts[current.phaseIndex] + current.phase.durationMs
        moveTo(boundary)
    }

    /**
     * Within the first [RESTART_THRESHOLD_MS] of a phase it goes back to the previous phase;
     * later (or in the first phase) it restarts the current phase, like a music player.
     */
    fun skipToPrevious() {
        val current = compute()
        if (stopped) return
        val phaseStart = phaseStarts[current.phaseIndex]
        val target = if (current.phaseElapsedMs > RESTART_THRESHOLD_MS || current.phaseIndex == 0) {
            phaseStart
        } else {
            phaseStarts[current.phaseIndex - 1]
        }
        moveTo(target)
    }

    /** Time the user actually spent working out: wall time minus pauses, ignoring skips. */
    fun activeDurationMs(): Long {
        val current = compute()
        val value = if (current.isFinished) totalMs - clockState.skipOffsetMs else rawActive(referenceNow())
        return value.coerceAtLeast(0)
    }

    private fun moveTo(positionMs: Long) {
        val position = positionAt(referenceNow())
        clockState = clockState.copy(skipOffsetMs = clockState.skipOffsetMs + (positionMs - position))
        tick()
    }

    private fun referenceNow(): Long = clockState.pausedAtElapsed ?: clock.elapsedRealtime()

    private fun rawActive(now: Long): Long = now - clockState.startElapsed - clockState.pausedTotalMs

    private fun positionAt(now: Long): Long = (rawActive(now) + clockState.skipOffsetMs).coerceAtLeast(0)

    private fun compute(): TimerState {
        val position = positionAt(referenceNow())
        val finished = position >= totalMs
        val clamped = position.coerceAtMost(totalMs)
        val index = if (finished) {
            plan.lastIndex
        } else {
            phaseStarts.indexOfLast { it <= clamped }
        }
        val phase = plan[index]
        val phaseElapsed = (clamped - phaseStarts[index]).coerceIn(0, phase.durationMs)
        val status = when {
            finished -> TimerStatus.FINISHED
            stopped -> TimerStatus.STOPPED
            clockState.pausedAtElapsed != null -> TimerStatus.PAUSED
            else -> TimerStatus.RUNNING
        }
        return TimerState(
            status = status,
            phaseIndex = index,
            phaseCount = plan.size,
            phase = phase,
            nextPhase = plan.getOrNull(index + 1),
            phaseElapsedMs = phaseElapsed,
            phaseRemainingMs = phase.durationMs - phaseElapsed,
            totalElapsedMs = clamped,
            totalRemainingMs = totalMs - clamped,
            totalDurationMs = totalMs,
        )
    }

    companion object {
        const val RESTART_THRESHOLD_MS = 2_000L
    }
}
