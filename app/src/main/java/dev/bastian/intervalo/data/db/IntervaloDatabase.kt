package dev.bastian.intervalo.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RoutineEntity::class, StepEntity::class, SessionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class IntervaloDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun sessionDao(): SessionDao
}
