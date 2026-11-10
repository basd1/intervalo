package dev.bastian.intervalo.domain

const val MAX_ROUNDS = 99
const val MAX_SECONDS = 3600
const val MAX_NAME_LENGTH = 40

enum class RoutineError {
    NAME_BLANK,
    NAME_TOO_LONG,
    NO_STEPS,
    ROUNDS_OUT_OF_RANGE,
    WARMUP_OUT_OF_RANGE,
    COOLDOWN_OUT_OF_RANGE,
}

data class RoutineValidation(
    val errors: Set<RoutineError>,
    /** Indices of steps whose duration is outside 1..MAX_SECONDS. */
    val invalidStepIndices: Set<Int>,
) {
    val isValid: Boolean get() = errors.isEmpty() && invalidStepIndices.isEmpty()
}

fun validateRoutine(routine: Routine): RoutineValidation {
    val errors = buildSet {
        if (routine.name.isBlank()) add(RoutineError.NAME_BLANK)
        if (routine.name.trim().length > MAX_NAME_LENGTH) add(RoutineError.NAME_TOO_LONG)
        if (routine.steps.isEmpty()) add(RoutineError.NO_STEPS)
        if (routine.rounds !in 1..MAX_ROUNDS) add(RoutineError.ROUNDS_OUT_OF_RANGE)
        if (routine.warmupSeconds !in 0..MAX_SECONDS) add(RoutineError.WARMUP_OUT_OF_RANGE)
        if (routine.cooldownSeconds !in 0..MAX_SECONDS) add(RoutineError.COOLDOWN_OUT_OF_RANGE)
    }
    val badSteps = routine.steps.withIndex()
        .filter { it.value.durationSeconds !in 1..MAX_SECONDS }
        .map { it.index }
        .toSet()
    return RoutineValidation(errors, badSteps)
}

/** Formats seconds as m:ss (or h:mm:ss from one hour). */
fun formatDuration(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = s % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
