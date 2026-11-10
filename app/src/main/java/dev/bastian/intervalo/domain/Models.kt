package dev.bastian.intervalo.domain

enum class StepType { WORK, REST }

data class Step(
    /** Optional label; blank means "use the default label of the type". */
    val name: String,
    val type: StepType,
    val durationSeconds: Int,
)

data class Routine(
    val id: Long = 0,
    val name: String,
    /** ARGB color used to identify the routine in lists and the widget. */
    val color: Int,
    val warmupSeconds: Int,
    val cooldownSeconds: Int,
    val rounds: Int,
    val steps: List<Step>,
) {
    val totalSeconds: Int
        get() = warmupSeconds + rounds * steps.sumOf { it.durationSeconds } + cooldownSeconds
}

data class Session(
    val id: Long = 0,
    /** Null when the routine was deleted after the session. */
    val routineId: Long?,
    val routineName: String,
    /** Wall-clock start, epoch millis. */
    val startedAt: Long,
    val durationSeconds: Int,
    val completed: Boolean,
)
