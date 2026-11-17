package ar.edu.uade.go2rondas.core.navigation

sealed class Screen(val route: String) {
    data object RobotSelection : Screen("robots")
    data object Panel : Screen("panel")
    data object Telemetry : Screen("telemetry")
    data object Missions : Screen("missions")
    data object Simulator : Screen("simulator")
    data object Alerts : Screen("alerts")
    data object AlertDetail : Screen("alerts/{alertId}") {
        const val ARG_ALERT_ID = "alertId"
        fun createRoute(alertId: String) = "alerts/$alertId"
    }
    data object History : Screen("history")
    data object MissionSummary : Screen("history/{missionId}") {
        const val ARG_MISSION_ID = "missionId"
        fun createRoute(missionId: String) = "history/$missionId"
    }
    data object CheckpointDetail : Screen("history/{missionId}/{checkpointId}") {
        const val ARG_MISSION_ID = "missionId"
        const val ARG_CHECKPOINT_ID = "checkpointId"
        fun createRoute(missionId: String, checkpointId: String) = "history/$missionId/$checkpointId"
    }
}
