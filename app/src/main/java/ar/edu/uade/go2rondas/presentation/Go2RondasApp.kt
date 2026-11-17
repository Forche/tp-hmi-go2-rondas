package ar.edu.uade.go2rondas.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.core.navigation.Screen
import ar.edu.uade.go2rondas.presentation.alerts.AlertDetailScreen
import ar.edu.uade.go2rondas.presentation.alerts.AlertsScreen
import ar.edu.uade.go2rondas.presentation.common.LocalSnackbarHostState
import ar.edu.uade.go2rondas.presentation.history.CheckpointDetailScreen
import ar.edu.uade.go2rondas.presentation.history.HistoryScreen
import ar.edu.uade.go2rondas.presentation.history.MissionSummaryScreen
import ar.edu.uade.go2rondas.presentation.missions.MissionsScreen
import ar.edu.uade.go2rondas.presentation.panel.PanelScreen
import ar.edu.uade.go2rondas.presentation.robotselection.RobotSelectionScreen
import ar.edu.uade.go2rondas.presentation.simulator.SimulatorScreen
import ar.edu.uade.go2rondas.presentation.telemetry.TelemetryScreen

/** Pestañas de la barra inferior. */
enum class AppTab(val screen: Screen, @param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    PANEL(Screen.Panel, R.string.nav_panel, R.drawable.ic_nav_panel),
    MISSIONS(Screen.Missions, R.string.nav_missions, R.drawable.ic_nav_missions),
    ALERTS(Screen.Alerts, R.string.nav_alerts, R.drawable.ic_nav_alerts),
    HISTORY(Screen.History, R.string.nav_history, R.drawable.ic_nav_history)
}

/** Cambia de pestaña sin apilar pantallas: vuelve al Panel y conserva el estado de cada pestaña. */
private fun NavHostController.openTab(tab: AppTab) = navigate(tab.screen.route) {
    popUpTo(Screen.Panel.route) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun Go2RondasApp() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            bottomBar = {
                if (AppTab.entries.any { it.screen.route == currentRoute }) {
                    NavigationBar {
                        AppTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.screen.route,
                                onClick = { navController.openTab(tab) },
                                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                                label = { Text(stringResource(tab.label)) }
                            )
                        }
                    }
                }
            },
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
                    PanelScreen(
                        onOpenTelemetry = { navController.navigate(Screen.Telemetry.route) },
                        onOpenMissions = { navController.openTab(AppTab.MISSIONS) },
                        onOpenAlert = { navController.navigate(Screen.AlertDetail.createRoute(it)) },
                        onOpenMissionSummary = { navController.navigate(Screen.MissionSummary.createRoute(it)) },
                        onOpenSimulator = { navController.navigate(Screen.Simulator.route) }
                    )
                }
                composable(Screen.Simulator.route) {
                    SimulatorScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.History.route) {
                    HistoryScreen(onOpenMission = { navController.navigate(Screen.MissionSummary.createRoute(it)) })
                }
                composable(
                    route = Screen.MissionSummary.route,
                    arguments = listOf(navArgument(Screen.MissionSummary.ARG_MISSION_ID) { type = NavType.StringType })
                ) {
                    MissionSummaryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCheckpoint = { missionId, checkpointId ->
                            navController.navigate(Screen.CheckpointDetail.createRoute(missionId, checkpointId))
                        }
                    )
                }
                composable(
                    route = Screen.CheckpointDetail.route,
                    arguments = listOf(
                        navArgument(Screen.CheckpointDetail.ARG_MISSION_ID) { type = NavType.StringType },
                        navArgument(Screen.CheckpointDetail.ARG_CHECKPOINT_ID) { type = NavType.StringType }
                    )
                ) {
                    CheckpointDetailScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Alerts.route) {
                    AlertsScreen(onOpenAlert = { navController.navigate(Screen.AlertDetail.createRoute(it)) })
                }
                composable(
                    route = Screen.AlertDetail.route,
                    arguments = listOf(navArgument(Screen.AlertDetail.ARG_ALERT_ID) { type = NavType.StringType })
                ) {
                    AlertDetailScreen(
                        onBack = { navController.popBackStack() },
                        onBackToPanel = { navController.openTab(AppTab.PANEL) }
                    )
                }
                composable(Screen.Telemetry.route) {
                    TelemetryScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Missions.route) {
                    MissionsScreen(onMissionStarted = { navController.openTab(AppTab.PANEL) })
                }
            }
        }
    }
}
