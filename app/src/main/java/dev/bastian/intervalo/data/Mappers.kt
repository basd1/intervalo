package dev.bastian.intervalo.data

import dev.bastian.intervalo.data.db.RoutineEntity
import dev.bastian.intervalo.data.db.RoutineWithSteps
import dev.bastian.intervalo.data.db.SessionEntity
import dev.bastian.intervalo.data.db.StepEntity
import dev.bastian.intervalo.domain.Routine
import dev.bastian.intervalo.domain.Session
import dev.bastian.intervalo.domain.Step
import dev.bastian.intervalo.domain.StepType

fun RoutineWithSteps.toDomain(): Routine = Routine(
    id = routine.id,
    name = routine.name,
    color = routine.color,
    warmupSeconds = routine.warmupSeconds,
    cooldownSeconds = routine.cooldownSeconds,
    rounds = routine.rounds,
    steps = steps.sortedBy { it.position }.map {
        Step(
            name = it.name,
            // An unknown value can only come from a future schema; treat it as work.
            type = StepType.entries.firstOrNull { type -> type.name == it.type } ?: StepType.WORK,
            durationSeconds = it.durationSeconds,
        )
    },
)

fun Routine.toEntity(): RoutineEntity = RoutineEntity(
    id = id,
    name = name.trim(),
    color = color,
    warmupSeconds = warmupSeconds,
    cooldownSeconds = cooldownSeconds,
    rounds = rounds,
)

fun Routine.toStepEntities(): List<StepEntity> = steps.mapIndexed { index, step ->
    StepEntity(
        routineId = id,
        position = index,
        name = step.name.trim(),
        type = step.type.name,
        durationSeconds = step.durationSeconds,
    )
}

fun SessionEntity.toDomain(): Session = Session(
    id = id,
    routineId = routineId,
    routineName = routineName,
    startedAt = startedAt,
    durationSeconds = durationSeconds,
    completed = completed,
)

fun Session.toEntity(): SessionEntity = SessionEntity(
    id = id,
    routineId = routineId,
    routineName = routineName,
    startedAt = startedAt,
    durationSeconds = durationSeconds,
    completed = completed,
)
