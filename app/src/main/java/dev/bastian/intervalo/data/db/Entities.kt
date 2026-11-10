package dev.bastian.intervalo.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val warmupSeconds: Int,
    val cooldownSeconds: Int,
    val rounds: Int,
)

@Entity(
    tableName = "steps",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId")],
)
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val position: Int,
    val name: String,
    /** `StepType.name`. */
    val type: String,
    val durationSeconds: Int,
)

data class RoutineWithSteps(
    @Embedded val routine: RoutineEntity,
    @Relation(parentColumn = "id", entityColumn = "routineId")
    val steps: List<StepEntity>,
)

/** History is kept after a routine is deleted, so there is no foreign key to `routines`. */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long?,
    val routineName: String,
    val startedAt: Long,
    val durationSeconds: Int,
    val completed: Boolean,
)
