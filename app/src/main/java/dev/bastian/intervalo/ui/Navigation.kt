package dev.bastian.intervalo.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.bastian.intervalo.ui.routines.RoutinesScreen
import kotlinx.serialization.Serializable

@Serializable
data object RoutinesRoute

@Composable
fun IntervaloNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = RoutinesRoute) {
        composable<RoutinesRoute> { RoutinesScreen() }
    }
}
