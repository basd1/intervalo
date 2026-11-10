package dev.bastian.intervalo

import android.app.Application
import dev.bastian.intervalo.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class IntervaloApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@IntervaloApp)
            modules(appModules)
        }
    }
}
