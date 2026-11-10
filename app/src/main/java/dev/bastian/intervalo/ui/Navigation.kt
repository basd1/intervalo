package dev.bastian.intervalo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.bastian.intervalo.ui.editor.EditorScreen
import dev.bastian.intervalo.ui.routines.RoutinesScreen
import dev.bastian.intervalo.ui.workout.WorkoutScreen
import kotlinx.serialization.Serializable

@Serializable
data object RoutinesRoute

/** `routineId == 0` creates a new routine. */
@Serializable
data class EditorRoute(val routineId: Long = 0)

@Serializable
data object WorkoutRoute

/**
 * @param openWorkoutRequest changes each time something outside the UI (the notification) asks to
 * show the workout screen; 0 means no request.
 */
@Composable
fun IntervaloNavHost(openWorkoutRequest: Int = 0) {
    val navController = rememberNavController()

    LaunchedEffect(openWorkoutRequest) {
        if (openWorkoutRequest > 0) navController.navigate(WorkoutRoute) { launchSingleTop = true }
    }

    NavHost(navController = navController, startDestination = RoutinesRoute) {
        composable<RoutinesRoute> {
            RoutinesScreen(
                onNewRoutine = { navController.navigate(EditorRoute()) },
                onEditRoutine = { navController.navigate(EditorRoute(it)) },
                onOpenWorkout = { navController.navigate(WorkoutRoute) { launchSingleTop = true } },
            )
        }
        composable<EditorRoute> { entry ->
            EditorScreen(
                routineId = entry.toRoute<EditorRoute>().routineId,
                onBack = { navController.popBackStack() },
            )
        }
        composable<WorkoutRoute> {
            WorkoutScreen(onClose = { navController.popBackStack(RoutinesRoute, inclusive = false) })
        }
    }
}
