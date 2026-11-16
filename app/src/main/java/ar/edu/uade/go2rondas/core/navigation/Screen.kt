package ar.edu.uade.go2rondas.core.navigation

sealed class Screen(val route: String) {
    data object RobotSelection : Screen("robots")
}
