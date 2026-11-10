package dev.bastian.intervalo.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dev.bastian.intervalo.data.SampleSeeder
import dev.bastian.intervalo.data.db.IntervaloDatabase
import dev.bastian.intervalo.data.repo.RoomRoutineRepository
import dev.bastian.intervalo.data.repo.RoomSessionRepository
import dev.bastian.intervalo.data.system.SystemClockSource
import dev.bastian.intervalo.domain.Clock
import dev.bastian.intervalo.domain.RoutineRepository
import dev.bastian.intervalo.domain.SessionRepository
import dev.bastian.intervalo.ui.editor.EditorViewModel
import dev.bastian.intervalo.ui.routines.RoutinesViewModel
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dataModule = module {
    single<Clock> { SystemClockSource() }
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
    single { SampleSeeder(get(), get()) }
}

val uiModule = module {
    viewModel { RoutinesViewModel(get()) }
    viewModel { (routineId: Long) -> EditorViewModel(routineId, get()) }
}

/** All Koin modules of the app, in the order they are loaded. */
val appModules: List<Module> = listOf(dataModule, uiModule)
