package ar.edu.uade.go2rondas.presentation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ar.edu.uade.go2rondas.core.navigation.Screen
import ar.edu.uade.go2rondas.presentation.common.LocalSnackbarHostState
import ar.edu.uade.go2rondas.presentation.panel.PanelScreen
import ar.edu.uade.go2rondas.presentation.robotselection.RobotSelectionScreen
import ar.edu.uade.go2rondas.presentation.telemetry.TelemetryScreen

@Composable
fun Go2RondasApp() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            // Cada pantalla maneja la barra de estado con su barra superior.
            contentWindowInsets = WindowInsets(0)
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Screen.RobotSelection.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(Screen.RobotSelection.route) {
                    RobotSelectionScreen(onOpenPanel = { navController.navigate(Screen.Panel.route) })
                }
                composable(Screen.Panel.route) {
                    PanelScreen(onOpenTelemetry = { navController.navigate(Screen.Telemetry.route) })
                }
                composable(Screen.Telemetry.route) {
                    TelemetryScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
