package dev.bastian.intervalo.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dev.bastian.intervalo.data.DataStoreActiveWorkoutStore
import dev.bastian.intervalo.data.DataStoreSettingsRepository
import dev.bastian.intervalo.data.SampleSeeder
import dev.bastian.intervalo.data.db.IntervaloDatabase
import dev.bastian.intervalo.data.repo.RoomRoutineRepository
import dev.bastian.intervalo.data.repo.RoomSessionRepository
import dev.bastian.intervalo.data.system.AndroidPhaseFeedback
import dev.bastian.intervalo.data.system.SystemClockSource
import dev.bastian.intervalo.domain.ActiveWorkoutStore
import dev.bastian.intervalo.domain.Clock
import dev.bastian.intervalo.domain.PhaseFeedback
import dev.bastian.intervalo.domain.RoutineRepository
import dev.bastian.intervalo.domain.SessionRepository
import dev.bastian.intervalo.domain.SettingsRepository
import dev.bastian.intervalo.domain.timer.WorkoutRunner
import dev.bastian.intervalo.ui.editor.EditorViewModel
import dev.bastian.intervalo.ui.routines.RoutinesViewModel
import dev.bastian.intervalo.ui.workout.WorkoutViewModel
import dev.bastian.intervalo.widget.WidgetModels
import dev.bastian.intervalo.widget.WidgetUpdater
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Scope that lives as long as the process, for work that must outlive any screen. */
val APP_SCOPE = named("app")

val dataModule = module {
    single<Clock> { SystemClockSource() }
    single<CoroutineScope>(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { Room.databaseBuilder(androidContext(), IntervaloDatabase::class.java, "intervalo.db").build() }
    single { get<IntervaloDatabase>().routineDao() }
    single { get<IntervaloDatabase>().sessionDao() }
    single<RoutineRepository> { RoomRoutineRepository(get()) }
    single<SessionRepository> { RoomSessionRepository(get()) }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(
            produceFile = { File(androidContext().filesDir, "datastore/intervalo.preferences_pb") },
        )
    }
    single<SettingsRepository> { DataStoreSettingsRepository(get()) }
    single<ActiveWorkoutStore> { DataStoreActiveWorkoutStore(get()) }
    single { SampleSeeder(get(), get()) }
}

val workoutModule = module {
    single<PhaseFeedback> { AndroidPhaseFeedback(androidContext()) }
    single {
        WorkoutRunner(
            routines = get(),
            sessions = get(),
            store = get(),
            settings = get(),
            clock = get(),
            feedback = get(),
            scope = get(APP_SCOPE),
        )
    }
}

val widgetModule = module {
    single { WidgetModels(get(), get(), get(), get()) }
    single { WidgetUpdater(androidContext(), get(), get(APP_SCOPE)) }
}

val uiModule = module {
    viewModel { RoutinesViewModel(get(), get()) }
    viewModel { (routineId: Long) -> EditorViewModel(routineId, get()) }
    viewModel { WorkoutViewModel(get(), get()) }
}

/** All Koin modules of the app, in the order they are loaded. */
val appModules: List<Module> = listOf(dataModule, workoutModule, widgetModule, uiModule)
