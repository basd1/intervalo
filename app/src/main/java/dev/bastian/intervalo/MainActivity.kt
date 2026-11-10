package dev.bastian.intervalo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.bastian.intervalo.domain.WorkoutState
import dev.bastian.intervalo.domain.timer.WorkoutRunner
import dev.bastian.intervalo.service.TimerService
import dev.bastian.intervalo.ui.IntervaloNavHost
import dev.bastian.intervalo.ui.theme.IntervaloTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val runner: WorkoutRunner by inject()
    private var openWorkoutRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            IntervaloTheme {
                IntervaloNavHost(openWorkoutRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        // If the process was killed during a workout, bring it back now that the app is visible
        // (starting a foreground service from the background is not always allowed).
        lifecycleScope.launch {
            if (runner.restore() && runner.state.value is WorkoutState.Active) TimerService.restore(this@MainActivity)
        }
    }

    private fun handle(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_OPEN_WORKOUT, false)) openWorkoutRequest++
    }

    companion object {
        const val EXTRA_OPEN_WORKOUT = "open_workout"
    }
}
