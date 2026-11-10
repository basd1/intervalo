package dev.bastian.intervalo.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.bastian.intervalo.ui.editor.EditorScreen
import dev.bastian.intervalo.ui.routines.RoutinesScreen
import kotlinx.serialization.Serializable

@Serializable
data object RoutinesRoute

/** `routineId == 0` creates a new routine. */
@Serializable
data class EditorRoute(val routineId: Long = 0)

@Composable
fun IntervaloNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = RoutinesRoute) {
        composable<RoutinesRoute> {
            RoutinesScreen(
                onNewRoutine = { navController.navigate(EditorRoute()) },
                onEditRoutine = { navController.navigate(EditorRoute(it)) },
            )
        }
        composable<EditorRoute> { entry ->
            EditorScreen(
                routineId = entry.toRoute<EditorRoute>().routineId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
