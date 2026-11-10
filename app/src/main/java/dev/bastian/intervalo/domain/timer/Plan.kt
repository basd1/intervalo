package dev.bastian.intervalo.domain.timer

import dev.bastian.intervalo.domain.Routine
import dev.bastian.intervalo.domain.StepType

enum class PhaseKind { WARMUP, WORK, REST, COOLDOWN }

/** One stretch of a workout. */
data class Phase(
    val kind: PhaseKind,
    /** Optional label from the routine; blank means "use the default label of the kind". */
    val name: String,
    val durationMs: Long,
    /** 1-based round, or 0 for warm-up and cool-down. */
    val round: Int,
)

/**
 * Flattens a routine into the sequence of phases to run: warm-up, every step of every round, cool-down.
 * A warm-up or cool-down of zero seconds produces no phase.
 */
fun buildPlan(routine: Routine): List<Phase> = buildList {
    if (routine.warmupSeconds > 0) {
        add(Phase(PhaseKind.WARMUP, "", routine.warmupSeconds * 1000L, 0))
    }
    for (round in 1..routine.rounds) {
        for (step in routine.steps) {
            val kind = if (step.type == StepType.WORK) PhaseKind.WORK else PhaseKind.REST
            add(Phase(kind, step.name, step.durationSeconds * 1000L, round))
        }
    }
    if (routine.cooldownSeconds > 0) {
        add(Phase(PhaseKind.COOLDOWN, "", routine.cooldownSeconds * 1000L, 0))
    }
}
