package ar.edu.uade.go2rondas.core.navigation

sealed class Screen(val route: String) {
    data object RobotSelection : Screen("robots")
    data object Panel : Screen("panel")
    data object Telemetry : Screen("telemetry")
    data object Missions : Screen("missions")
    data object Alerts : Screen("alerts")
    data object AlertDetail : Screen("alerts/{alertId}") {
        const val ARG_ALERT_ID = "alertId"
        fun createRoute(alertId: String) = "alerts/$alertId"
    }
}
