package dev.bastian.intervalo

import android.app.Application
import dev.bastian.intervalo.data.SampleSeeder
import dev.bastian.intervalo.di.appModules
import dev.bastian.intervalo.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class IntervaloApp : Application() {
    /** Lives as long as the process; for work that must outlive any screen. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sampleSeeder: SampleSeeder by inject()
    private val widgetUpdater: WidgetUpdater by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@IntervaloApp)
            modules(appModules)
        }
        applicationScope.launch { sampleSeeder.seedIfNeeded() }
        widgetUpdater.start()
    }
}
